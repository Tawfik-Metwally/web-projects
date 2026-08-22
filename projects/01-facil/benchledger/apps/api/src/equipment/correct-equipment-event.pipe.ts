import { Injectable, type PipeTransform } from "@nestjs/common";

import { rejectUnknownFields, requireInputRecord, requireTrimmedText } from "./input-validation.js";

export interface CorrectEquipmentEventInput {
  note: string;
}

const allowedFields = new Set(["note"]);

@Injectable()
export class CorrectEquipmentEventPipe implements PipeTransform<unknown, CorrectEquipmentEventInput> {
  public transform(value: unknown): CorrectEquipmentEventInput {
    const input = requireInputRecord(value);
    rejectUnknownFields(input, allowedFields);
    return { note: requireTrimmedText(input.note, "note", 500) };
  }
}
