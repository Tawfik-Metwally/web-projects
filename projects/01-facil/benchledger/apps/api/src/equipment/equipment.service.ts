import { ConflictException, Inject, Injectable, NotFoundException } from "@nestjs/common";
import { deriveEquipmentStatus } from "@benchledger/domain";

import { PrismaService } from "../database/prisma.service.js";
import type { CreateEquipmentInput } from "./create-equipment.pipe.js";
import type { CreateEquipmentEventInput } from "./create-equipment-event.pipe.js";
import type { UpdateEquipmentInput } from "./update-equipment.pipe.js";

interface EventRecord {
  id: string;
  type: "CALIBRATION" | "MAINTENANCE" | "OUT_OF_SERVICE" | "RETURNED_TO_SERVICE" | "CORRECTION";
  occurredAt: Date;
  successful: boolean | null;
  note: string | null;
  correctsEventId: string | null;
  createdAt: Date;
}

interface EquipmentRecord {
  id: string;
  assetTag: string;
  name: string;
  category: string;
  manufacturer: string | null;
  model: string | null;
  serialNumber: string | null;
  location: string;
  calibrationIntervalDays: number | null;
  archivedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
  events: EventRecord[];
}

const eventOrder = { occurredAt: "desc" as const };

function toEquipmentView(equipment: EquipmentRecord) {
  const correctedEventIds = new Set(
    equipment.events.flatMap((event) => event.type === "CORRECTION" && event.correctsEventId ? [event.correctsEventId] : []),
  );
  const effectiveEvents = equipment.events.filter(
    (event) => event.type !== "CORRECTION" && !correctedEventIds.has(event.id),
  );
  const lastCalibrationAttempt = effectiveEvents.find((event) => event.type === "CALIBRATION");
  const lastSuccessfulCalibration = effectiveEvents.find(
    (event) => event.type === "CALIBRATION" && event.successful === true,
  );
  const derived = deriveEquipmentStatus({
    asOf: new Date(),
    isOutOfService: equipment.archivedAt !== null,
    calibrationIntervalDays: equipment.calibrationIntervalDays,
    lastSuccessfulCalibrationAt: lastSuccessfulCalibration?.occurredAt ?? null,
    lastFailedCalibrationAt: lastCalibrationAttempt?.successful === false ? lastCalibrationAttempt.occurredAt : null,
  });

  return {
    ...equipment,
    ...derived,
    lastSuccessfulCalibrationAt: lastSuccessfulCalibration?.occurredAt ?? null,
  };
}

function isUniqueConstraintError(error: unknown): error is { code: "P2002" } {
  return typeof error === "object" && error !== null && "code" in error && error.code === "P2002";
}

@Injectable()
export class EquipmentService {
  public constructor(@Inject(PrismaService) private readonly prisma: PrismaService) {}

  public async create(input: CreateEquipmentInput) {
    try {
      const created = await this.prisma.equipment.create({ data: input });
      return await this.findOne(created.id);
    } catch (error) {
      if (isUniqueConstraintError(error)) {
        throw new ConflictException("asset tag or serial number already exists");
      }

      throw error;
    }
  }

  public findAll() {
    return this.prisma.equipment.findMany({
      orderBy: [{ createdAt: "desc" }, { name: "asc" }],
      include: { events: { orderBy: eventOrder } },
    }).then((equipment) => equipment.map(toEquipmentView));
  }

  public async findOne(id: string) {
    const equipment = await this.prisma.equipment.findUnique({
      where: { id },
      include: { events: { orderBy: eventOrder } },
    });

    if (!equipment) {
      throw new NotFoundException("equipment not found");
    }

    return toEquipmentView(equipment);
  }

  public async update(id: string, input: UpdateEquipmentInput) {
    await this.ensureExists(id);
    try {
      await this.prisma.equipment.update({ where: { id }, data: input });
      return await this.findOne(id);
    } catch (error) {
      if (isUniqueConstraintError(error)) {
        throw new ConflictException("serial number already exists");
      }
      throw error;
    }
  }

  public async addEvent(id: string, input: CreateEquipmentEventInput) {
    await this.ensureExists(id);
    await this.prisma.equipmentEvent.create({
      data: {
        equipmentId: id,
        type: input.type,
        occurredAt: input.occurredAt,
        successful: input.successful,
        note: input.note,
      },
    });
    return this.findOne(id);
  }

  public async correctEvent(id: string, eventId: string, note: string) {
    await this.ensureExists(id);
    const event = await this.prisma.equipmentEvent.findFirst({ where: { id: eventId, equipmentId: id } });
    if (!event || (event.type !== "CALIBRATION" && event.type !== "MAINTENANCE")) {
      throw new NotFoundException("correctable event not found");
    }
    const existingCorrection = await this.prisma.equipmentEvent.findUnique({ where: { correctsEventId: eventId } });
    if (existingCorrection) {
      throw new ConflictException("event already has a correction");
    }
    await this.prisma.equipmentEvent.create({
      data: {
        equipmentId: id,
        type: "CORRECTION",
        occurredAt: new Date(),
        note,
        correctsEventId: eventId,
      },
    });
    return this.findOne(id);
  }

  public async archive(id: string, note?: string) {
    const equipment = await this.ensureExists(id);
    if (equipment.archivedAt) {
      throw new ConflictException("equipment is already out of service");
    }

    const occurredAt = new Date();
    await this.prisma.$transaction([
      this.prisma.equipment.update({ where: { id }, data: { archivedAt: occurredAt } }),
      this.prisma.equipmentEvent.create({
        data: { equipmentId: id, type: "OUT_OF_SERVICE", occurredAt, note },
      }),
    ]);
    return this.findOne(id);
  }

  public async restore(id: string, note?: string) {
    const equipment = await this.ensureExists(id);
    if (!equipment.archivedAt) {
      throw new ConflictException("equipment is already in service");
    }

    const occurredAt = new Date();
    await this.prisma.$transaction([
      this.prisma.equipment.update({ where: { id }, data: { archivedAt: null } }),
      this.prisma.equipmentEvent.create({
        data: { equipmentId: id, type: "RETURNED_TO_SERVICE", occurredAt, note },
      }),
    ]);
    return this.findOne(id);
  }

  private async ensureExists(id: string) {
    const equipment = await this.prisma.equipment.findUnique({ where: { id } });
    if (!equipment) {
      throw new NotFoundException("equipment not found");
    }
    return equipment;
  }
}
