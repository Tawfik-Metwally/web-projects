import { BadRequestException, Injectable, type PipeTransform } from "@nestjs/common";

export interface CreateEquipmentInput {
  assetTag: string;
  name: string;
  category: string;
  manufacturer?: string;
  model?: string;
  serialNumber?: string;
  location: string;
  calibrationIntervalDays?: number;
}

type InputRecord = Record<string, unknown>;

const allowedFields = new Set([
  "assetTag",
  "name",
  "category",
  "manufacturer",
  "model",
  "serialNumber",
  "location",
  "calibrationIntervalDays",
]);

function isRecord(value: unknown): value is InputRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function requiredText(input: InputRecord, field: string, maxLength: number): string {
  const value = input[field];

  if (typeof value !== "string" || value.trim().length === 0) {
    throw new BadRequestException(`${field} must be a non-empty string`);
  }

  const normalized = value.trim();
  if (normalized.length > maxLength) {
    throw new BadRequestException(`${field} must have at most ${maxLength} characters`);
  }

  return normalized;
}

function optionalText(input: InputRecord, field: string, maxLength: number): string | undefined {
  const value = input[field];

  if (value === undefined || value === null || value === "") {
    return undefined;
  }

  if (typeof value !== "string") {
    throw new BadRequestException(`${field} must be a string`);
  }

  const normalized = value.trim();
  if (normalized.length === 0 || normalized.length > maxLength) {
    throw new BadRequestException(`${field} must have between 1 and ${maxLength} characters`);
  }

  return normalized;
}

@Injectable()
export class CreateEquipmentPipe implements PipeTransform<unknown, CreateEquipmentInput> {
  public transform(value: unknown): CreateEquipmentInput {
    if (!isRecord(value)) {
      throw new BadRequestException("request body must be an object");
    }

    const unknownFields = Object.keys(value).filter((field) => !allowedFields.has(field));
    if (unknownFields.length > 0) {
      throw new BadRequestException(`unknown fields: ${unknownFields.join(", ")}`);
    }

    const interval = value.calibrationIntervalDays;
    if (interval !== undefined && interval !== null && (!Number.isInteger(interval) || Number(interval) <= 0)) {
      throw new BadRequestException("calibrationIntervalDays must be a positive integer");
    }

    return {
      assetTag: requiredText(value, "assetTag", 50),
      name: requiredText(value, "name", 120),
      category: requiredText(value, "category", 80),
      location: requiredText(value, "location", 100),
      manufacturer: optionalText(value, "manufacturer", 100),
      model: optionalText(value, "model", 100),
      serialNumber: optionalText(value, "serialNumber", 100),
      calibrationIntervalDays: interval === undefined || interval === null ? undefined : Number(interval),
    };
  }
}
