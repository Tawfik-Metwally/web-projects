import { BadRequestException, Injectable, type PipeTransform } from "@nestjs/common";

import { rejectUnknownFields, requireInputRecord, requireTrimmedText, type InputRecord } from "./input-validation.js";

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

function requiredText(input: InputRecord, field: string, maxLength: number): string {
  return requireTrimmedText(input[field], field, maxLength);
}

function optionalText(input: InputRecord, field: string, maxLength: number): string | undefined {
  const value = input[field];

  if (value === undefined || value === null || value === "") {
    return undefined;
  }

  return requireTrimmedText(value, field, maxLength);
}

@Injectable()
export class CreateEquipmentPipe implements PipeTransform<unknown, CreateEquipmentInput> {
  public transform(value: unknown): CreateEquipmentInput {
    const input = requireInputRecord(value);
    rejectUnknownFields(input, allowedFields);

    const interval = input.calibrationIntervalDays;
    if (interval !== undefined && interval !== null && (!Number.isInteger(interval) || Number(interval) <= 0)) {
      throw new BadRequestException("calibrationIntervalDays must be a positive integer");
    }

    return {
      assetTag: requiredText(input, "assetTag", 50),
      name: requiredText(input, "name", 120),
      category: requiredText(input, "category", 80),
      location: requiredText(input, "location", 100),
      manufacturer: optionalText(input, "manufacturer", 100),
      model: optionalText(input, "model", 100),
      serialNumber: optionalText(input, "serialNumber", 100),
      calibrationIntervalDays: interval === undefined || interval === null ? undefined : Number(interval),
    };
  }
}
