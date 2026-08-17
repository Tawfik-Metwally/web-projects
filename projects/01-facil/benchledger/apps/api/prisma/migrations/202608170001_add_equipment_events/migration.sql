CREATE TYPE "EquipmentEventType" AS ENUM (
  'CALIBRATION',
  'MAINTENANCE',
  'OUT_OF_SERVICE',
  'RETURNED_TO_SERVICE'
);

ALTER TABLE "Equipment"
ADD COLUMN "archivedAt" TIMESTAMPTZ(3);

CREATE TABLE "EquipmentEvent" (
  "id" UUID NOT NULL,
  "equipmentId" UUID NOT NULL,
  "type" "EquipmentEventType" NOT NULL,
  "occurredAt" TIMESTAMPTZ(3) NOT NULL,
  "successful" BOOLEAN,
  "note" VARCHAR(500),
  "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT "EquipmentEvent_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "EquipmentEvent_equipmentId_fkey"
    FOREIGN KEY ("equipmentId") REFERENCES "Equipment"("id")
    ON DELETE RESTRICT ON UPDATE CASCADE
);

CREATE INDEX "Equipment_archivedAt_idx" ON "Equipment"("archivedAt");
CREATE INDEX "EquipmentEvent_equipmentId_occurredAt_idx"
  ON "EquipmentEvent"("equipmentId", "occurredAt" DESC);
CREATE INDEX "EquipmentEvent_type_occurredAt_idx"
  ON "EquipmentEvent"("type", "occurredAt" DESC);
