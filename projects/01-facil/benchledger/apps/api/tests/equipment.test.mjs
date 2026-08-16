import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { BadRequestException, ConflictException, NotFoundException } from "@nestjs/common";

import { CreateEquipmentPipe } from "../dist/equipment/create-equipment.pipe.js";
import { EquipmentService } from "../dist/equipment/equipment.service.js";

const input = {
  assetTag: "OSC-001",
  name: "Digital oscilloscope",
  category: "Measurement",
  location: "Electronics bench",
  calibrationIntervalDays: 365,
};

describe("CreateEquipmentPipe", () => {
  it("normalizes accepted input", () => {
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
});

describe("EquipmentService", () => {
  it("creates and lists equipment through the persistence boundary", async () => {
    const stored = { id: "45ec7ab7-a193-4784-8277-73559fd70d45", ...input };
    const prisma = {
      equipment: {
        create: async ({ data }) => ({ id: stored.id, ...data }),
        findMany: async () => [stored],
        findUnique: async () => stored,
      },
    };
    const service = new EquipmentService(prisma);

    assert.deepEqual(await service.create(input), stored);
    assert.deepEqual(await service.findAll(), [stored]);
    assert.deepEqual(await service.findOne(stored.id), stored);
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
