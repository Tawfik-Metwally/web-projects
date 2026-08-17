ALTER TYPE "EquipmentEventType" ADD VALUE 'CORRECTION';

ALTER TABLE "EquipmentEvent"
ADD COLUMN "correctsEventId" UUID;

CREATE UNIQUE INDEX "EquipmentEvent_correctsEventId_key"
ON "EquipmentEvent"("correctsEventId");

ALTER TABLE "EquipmentEvent"
ADD CONSTRAINT "EquipmentEvent_correctsEventId_fkey"
FOREIGN KEY ("correctsEventId") REFERENCES "EquipmentEvent"("id")
ON DELETE RESTRICT ON UPDATE CASCADE;
