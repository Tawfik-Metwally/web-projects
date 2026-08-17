import { BadRequestException, Injectable, type PipeTransform } from "@nestjs/common";

export interface UpdateEquipmentInput {
  name?: string;
  category?: string;
  manufacturer?: string | null;
  model?: string | null;
  serialNumber?: string | null;
  location?: string;
  calibrationIntervalDays?: number | null;
}

type InputRecord = Record<string, unknown>;

const fieldLimits = {
  name: 120,
  category: 80,
  manufacturer: 100,
  model: 100,
  serialNumber: 100,
  location: 100,
} as const;

function isRecord(value: unknown): value is InputRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

@Injectable()
export class UpdateEquipmentPipe implements PipeTransform<unknown, UpdateEquipmentInput> {
  public transform(value: unknown): UpdateEquipmentInput {
    if (!isRecord(value)) {
      throw new BadRequestException("request body must be an object");
    }

    const unknownFields = Object.keys(value).filter((field) => !(field in fieldLimits) && field !== "calibrationIntervalDays");
    if (unknownFields.length > 0) {
      throw new BadRequestException(`unknown fields: ${unknownFields.join(", ")}`);
    }

    if (Object.keys(value).length === 0) {
      throw new BadRequestException("at least one field is required");
    }

    const result: UpdateEquipmentInput = {};
    for (const [field, maxLength] of Object.entries(fieldLimits)) {
      if (!(field in value)) continue;
      const raw = value[field];
      const nullable = field === "manufacturer" || field === "model" || field === "serialNumber";

      if (nullable && (raw === null || raw === "")) {
        Object.assign(result, { [field]: null });
        continue;
      }

      if (typeof raw !== "string" || raw.trim().length === 0 || raw.trim().length > maxLength) {
        throw new BadRequestException(`${field} must have between 1 and ${maxLength} characters`);
      }
      Object.assign(result, { [field]: raw.trim() });
    }

    if ("calibrationIntervalDays" in value) {
      const interval = value.calibrationIntervalDays;
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
