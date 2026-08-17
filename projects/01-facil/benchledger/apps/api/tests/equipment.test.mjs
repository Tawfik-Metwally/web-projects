import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { BadRequestException, ConflictException, NotFoundException } from "@nestjs/common";

import { CreateEquipmentPipe } from "../dist/equipment/create-equipment.pipe.js";
import { CorrectEquipmentEventPipe } from "../dist/equipment/correct-equipment-event.pipe.js";
import { CreateEquipmentEventPipe } from "../dist/equipment/create-equipment-event.pipe.js";
import { EquipmentService } from "../dist/equipment/equipment.service.js";
import { UpdateEquipmentPipe } from "../dist/equipment/update-equipment.pipe.js";

const input = {
  assetTag: "OSC-001",
  name: "Digital oscilloscope",
  category: "Measurement",
  location: "Electronics bench",
  calibrationIntervalDays: 365,
};

const stored = {
  id: "45ec7ab7-a193-4784-8277-73559fd70d45",
  ...input,
  manufacturer: null,
  model: null,
  serialNumber: null,
  archivedAt: null,
  createdAt: new Date("2026-08-16T12:00:00.000Z"),
  updatedAt: new Date("2026-08-16T12:00:00.000Z"),
  events: [],
};

describe("equipment input validation", () => {
  it("normalizes accepted creation input", () => {
    const result = new CreateEquipmentPipe().transform({ ...input, name: "  Digital oscilloscope  " });
    assert.equal(result.name, "Digital oscilloscope");
  });

  it("rejects unknown fields and invalid intervals", () => {
    assert.throws(() => new CreateEquipmentPipe().transform({ ...input, owner: "hidden" }), BadRequestException);
    assert.throws(
      () => new CreateEquipmentPipe().transform({ ...input, calibrationIntervalDays: 0 }),
      BadRequestException,
    );
  });

  it("supports controlled metadata updates without changing the asset tag", () => {
    const pipe = new UpdateEquipmentPipe();
    assert.deepEqual(pipe.transform({ name: "  Scope A  ", manufacturer: "" }), {
      name: "Scope A",
      manufacturer: null,
    });
    assert.throws(() => pipe.transform({ assetTag: "CHANGED" }), BadRequestException);
    assert.throws(() => pipe.transform({}), BadRequestException);
  });

  it("requires explicit calibration outcomes and valid event dates", () => {
    const pipe = new CreateEquipmentEventPipe();
    assert.equal(
      pipe.transform({
        type: "CALIBRATION",
        occurredAt: "2026-08-17T12:00:00.000Z",
        successful: true,
      }).successful,
      true,
    );
    assert.throws(
      () => pipe.transform({ type: "CALIBRATION", occurredAt: "2026-08-17T12:00:00.000Z" }),
      BadRequestException,
    );
  });

  it("requires a reason when appending a correction", () => {
    const pipe = new CorrectEquipmentEventPipe();
    assert.deepEqual(pipe.transform({ note: " Wrong timestamp " }), { note: "Wrong timestamp" });
    assert.throws(() => pipe.transform({ note: "" }), BadRequestException);
  });
});

describe("EquipmentService", () => {
  it("creates, lists and derives status through the persistence boundary", async () => {
    const prisma = {
      equipment: {
        create: async ({ data }) => ({ id: stored.id, ...data }),
        findMany: async () => [stored],
        findUnique: async () => stored,
      },
    };
    const service = new EquipmentService(prisma);

    assert.equal((await service.create(input)).status, "OVERDUE");
    assert.equal((await service.findAll())[0].status, "OVERDUE");
    assert.equal((await service.findOne(stored.id)).assetTag, "OSC-001");
  });

  it("appends a state event instead of deleting equipment", async () => {
    const operations = [];
    const archived = { ...stored, archivedAt: new Date("2026-08-17T12:00:00.000Z") };
    let current = stored;
    const prisma = {
      equipment: {
        findUnique: async () => current,
        update: (operation) => {
          operations.push({ kind: "update", operation });
          current = archived;
          return Promise.resolve(archived);
        },
      },
      equipmentEvent: {
        create: (operation) => {
          operations.push({ kind: "event", operation });
          return Promise.resolve({});
        },
      },
      $transaction: async (promises) => Promise.all(promises),
    };
    const service = new EquipmentService(prisma);
    service.findOne = async () => ({ ...archived, status: "OUT_OF_SERVICE" });

    const result = await service.archive(stored.id, "Awaiting repair");
    assert.equal(result.status, "OUT_OF_SERVICE");
    assert.equal(operations[1].operation.data.type, "OUT_OF_SERVICE");
  });

  it("surfaces failed calibration and ignores it after an append-only correction", async () => {
    const failedEvent = {
      id: "d66e1ce7-c211-44c6-b3c5-913d00f9ba29",
      type: "CALIBRATION",
      occurredAt: new Date("2026-08-17T13:45:00.000Z"),
      successful: false,
      note: "Outside tolerance",
      correctsEventId: null,
      createdAt: new Date("2026-08-17T13:46:00.000Z"),
    };
    const correction = {
      id: "a5d5ed31-2cc3-44f0-b5e7-b4cb72ceafef",
      type: "CORRECTION",
      occurredAt: new Date("2026-08-17T14:00:00.000Z"),
      successful: null,
      note: "Event belonged to another asset",
      correctsEventId: failedEvent.id,
      createdAt: new Date("2026-08-17T14:00:00.000Z"),
    };
    let events = [failedEvent];
    const service = new EquipmentService({
      equipment: {
        findUnique: async () => ({ ...stored, events }),
      },
    });

    assert.equal((await service.findOne(stored.id)).status, "CALIBRATION_FAILED");
    events = [correction, failedEvent];
    assert.equal((await service.findOne(stored.id)).status, "OVERDUE");
  });

  it("maps persistence outcomes to stable HTTP errors", async () => {
    const conflictService = new EquipmentService({
      equipment: {
        create: async () => Promise.reject({ code: "P2002" }),
      },
    });
    await assert.rejects(() => conflictService.create(input), ConflictException);

    const missingService = new EquipmentService({
      equipment: {
        findUnique: async () => null,
      },
    });
    await assert.rejects(
      () => missingService.findOne("45ec7ab7-a193-4784-8277-73559fd70d45"),
      NotFoundException,
    );
  });
});
