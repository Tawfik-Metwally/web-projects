import { BadRequestException, Injectable, type PipeTransform } from "@nestjs/common";

import { rejectUnknownFields, requireInputRecord, requireTrimmedText } from "./input-validation.js";

export interface UpdateEquipmentInput {
  name?: string;
  category?: string;
  manufacturer?: string | null;
  model?: string | null;
  serialNumber?: string | null;
  location?: string;
  calibrationIntervalDays?: number | null;
}

const fieldLimits = {
  name: 120,
  category: 80,
  manufacturer: 100,
  model: 100,
  serialNumber: 100,
  location: 100,
} as const;

const allowedFields = new Set([...Object.keys(fieldLimits), "calibrationIntervalDays"]);

@Injectable()
export class UpdateEquipmentPipe implements PipeTransform<unknown, UpdateEquipmentInput> {
  public transform(value: unknown): UpdateEquipmentInput {
    const input = requireInputRecord(value);
    rejectUnknownFields(input, allowedFields);

    if (Object.keys(input).length === 0) {
      throw new BadRequestException("at least one field is required");
    }

    const result: UpdateEquipmentInput = {};
    for (const [field, maxLength] of Object.entries(fieldLimits)) {
      if (!(field in input)) continue;
      const raw = input[field];
      const nullable = field === "manufacturer" || field === "model" || field === "serialNumber";

      if (nullable && (raw === null || raw === "")) {
        Object.assign(result, { [field]: null });
        continue;
      }

      Object.assign(result, { [field]: requireTrimmedText(raw, field, maxLength) });
    }

    if ("calibrationIntervalDays" in input) {
      const interval = input.calibrationIntervalDays;
      if (interval === null) {
        result.calibrationIntervalDays = null;
      } else if (!Number.isInteger(interval) || Number(interval) <= 0) {
        throw new BadRequestException("calibrationIntervalDays must be a positive integer or null");
      } else {
        result.calibrationIntervalDays = Number(interval);
      }
    }

    return result;
  }
}
