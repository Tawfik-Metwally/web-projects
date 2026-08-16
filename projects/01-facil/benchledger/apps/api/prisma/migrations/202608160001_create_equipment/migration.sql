CREATE TABLE "Equipment" (
    "id" UUID NOT NULL,
    "assetTag" VARCHAR(50) NOT NULL,
    "name" VARCHAR(120) NOT NULL,
    "category" VARCHAR(80) NOT NULL,
    "manufacturer" VARCHAR(100),
    "model" VARCHAR(100),
    "serialNumber" VARCHAR(100),
    "location" VARCHAR(100) NOT NULL,
    "calibrationIntervalDays" INTEGER,
    "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(3) NOT NULL,

    CONSTRAINT "Equipment_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "Equipment_calibration_interval_positive"
        CHECK ("calibrationIntervalDays" IS NULL OR "calibrationIntervalDays" > 0)
);

CREATE UNIQUE INDEX "Equipment_assetTag_key" ON "Equipment"("assetTag");
CREATE UNIQUE INDEX "Equipment_serialNumber_key" ON "Equipment"("serialNumber");
CREATE INDEX "Equipment_category_idx" ON "Equipment"("category");
CREATE INDEX "Equipment_location_idx" ON "Equipment"("location");
