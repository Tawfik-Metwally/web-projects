ALTER TYPE "EquipmentEventType" ADD VALUE 'DELETED';
ALTER TYPE "EquipmentEventType" ADD VALUE 'RESTORED_FROM_DELETION';

ALTER TABLE "Equipment" ADD COLUMN "deletedAt" TIMESTAMPTZ(3);

CREATE INDEX "Equipment_deletedAt_idx" ON "Equipment"("deletedAt");
