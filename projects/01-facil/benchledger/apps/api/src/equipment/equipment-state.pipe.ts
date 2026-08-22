import { BadRequestException, Injectable, type PipeTransform } from "@nestjs/common";

import { rejectUnknownFields, requireInputRecord } from "./input-validation.js";

export interface EquipmentStateInput {
  note?: string;
}

const allowedFields = new Set(["note"]);

@Injectable()
export class EquipmentStatePipe implements PipeTransform<unknown, EquipmentStateInput> {
  public transform(value: unknown): EquipmentStateInput {
    if (value === undefined || value === null) return {};
    const input = requireInputRecord(value);
    rejectUnknownFields(input, allowedFields);

    if (input.note === undefined || input.note === null || input.note === "") return {};
    if (typeof input.note !== "string" || input.note.trim().length > 500) {
      throw new BadRequestException("note must have at most 500 characters");
    }

    return { note: input.note.trim() };
  }
}
