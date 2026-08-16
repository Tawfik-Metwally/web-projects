import { ConflictException, Inject, Injectable, NotFoundException } from "@nestjs/common";

import { PrismaService } from "../database/prisma.service.js";
import type { CreateEquipmentInput } from "./create-equipment.pipe.js";

function isUniqueConstraintError(error: unknown): error is { code: "P2002" } {
  return typeof error === "object" && error !== null && "code" in error && error.code === "P2002";
}

@Injectable()
export class EquipmentService {
  public constructor(@Inject(PrismaService) private readonly prisma: PrismaService) {}

  public async create(input: CreateEquipmentInput) {
    try {
      return await this.prisma.equipment.create({ data: input });
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
    });
  }

  public async findOne(id: string) {
    const equipment = await this.prisma.equipment.findUnique({ where: { id } });

    if (!equipment) {
      throw new NotFoundException("equipment not found");
    }

    return equipment;
  }
}
