import { Module } from "@nestjs/common";

import { CreateEquipmentPipe } from "./create-equipment.pipe.js";
import { EquipmentController } from "./equipment.controller.js";
import { EquipmentService } from "./equipment.service.js";

@Module({
  controllers: [EquipmentController],
  providers: [EquipmentService, CreateEquipmentPipe],
})
export class EquipmentModule {}
