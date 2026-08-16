import { Body, Controller, Get, Inject, Param, ParseUUIDPipe, Post } from "@nestjs/common";

import { CreateEquipmentPipe, type CreateEquipmentInput } from "./create-equipment.pipe.js";
import { EquipmentService } from "./equipment.service.js";

@Controller("equipment")
export class EquipmentController {
  public constructor(@Inject(EquipmentService) private readonly equipmentService: EquipmentService) {}

  @Post()
  public create(@Body(CreateEquipmentPipe) input: CreateEquipmentInput) {
    return this.equipmentService.create(input);
  }

  @Get()
  public findAll() {
    return this.equipmentService.findAll();
  }

  @Get(":id")
  public findOne(@Param("id", new ParseUUIDPipe({ version: "4" })) id: string) {
    return this.equipmentService.findOne(id);
  }
}
