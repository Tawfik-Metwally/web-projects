import {
  ArchiveOutlined,
  BuildOutlined,
  CloseOutlined,
  DeleteOutlineOutlined,
  EditOutlined,
  RestoreOutlined,
  ScienceOutlined,
} from "@mui/icons-material";
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemIcon,
  Stack,
  Typography,
} from "@mui/material";

import { eventLabels, formatDate, formatDateTime, StatusChip } from "./equipment-display.js";
import type { Equipment, EquipmentEvent } from "./equipment.js";

interface EquipmentDetailsDrawerProps {
  equipment: Equipment;
  onClose: () => void;
  onEdit: () => void;
  onRecordEvent: () => void;
  onCorrect: (event: EquipmentEvent) => void;
  onRestoreDeleted: () => void;
  onChangeState: () => void;
  onDelete: () => void;
}

function EventIcon({ event }: { event: EquipmentEvent }) {
  if (event.type === "CALIBRATION") return <ScienceOutlined fontSize="small" />;
  if (event.type === "MAINTENANCE") return <BuildOutlined fontSize="small" />;
  if (event.type === "RETURNED_TO_SERVICE" || event.type === "RESTORED_FROM_DELETION") return <RestoreOutlined fontSize="small" />;
  if (event.type === "DELETED") return <DeleteOutlineOutlined fontSize="small" />;
  if (event.type === "CORRECTION") return <EditOutlined fontSize="small" />;
  return <ArchiveOutlined fontSize="small" />;
}

export function EquipmentDetailsDrawer({
  equipment,
  onClose,
  onEdit,
  onRecordEvent,
  onCorrect,
  onRestoreDeleted,
  onChangeState,
  onDelete,
}: EquipmentDetailsDrawerProps) {
  const history = equipment.events.filter((event) => event.type !== "CORRECTION");
  const correctionCount = equipment.events.length - history.length;

  return (
    <Drawer anchor="right" open onClose={onClose} slotProps={{ paper: { sx: { width: { xs: "100%", sm: 440 } } } }}>
      <Box sx={{ p: 2.5 }}>
        <Stack direction="row" spacing={2} sx={{ justifyContent: "space-between", alignItems: "flex-start" }}>
          <Box>
            <Typography sx={{ fontFamily: "ui-monospace, Consolas, monospace", color: "primary.main", fontSize: "0.78rem", fontWeight: 750 }}>{equipment.assetTag}</Typography>
            <Typography variant="h2" sx={{ mt: 0.5 }}>{equipment.name}</Typography>
          </Box>
          <Stack direction="row" spacing={0.5} sx={{ alignItems: "center" }}>
            <StatusChip status={equipment.status} />
            <IconButton aria-label="Close equipment details" onClick={onClose} size="small"><CloseOutlined fontSize="small" /></IconButton>
          </Stack>
        </Stack>

        {equipment.deletedAt ? (
          <Alert severity="info" sx={{ my: 2 }}>Deleted {formatDateTime(equipment.deletedAt)}. Restore this record before editing it or recording events.</Alert>
        ) : (
          <Stack direction="row" spacing={1} sx={{ my: 2 }}>
            <Button size="small" variant="outlined" startIcon={<EditOutlined />} onClick={onEdit}>Edit</Button>
            <Button size="small" variant="outlined" startIcon={<BuildOutlined />} onClick={onRecordEvent}>Record event</Button>
          </Stack>
        )}

        <Divider />
        <Box sx={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 2, py: 2.25 }}>
          {[
            ["Category", equipment.category],
            ["Location", equipment.location],
            ["Manufacturer", equipment.manufacturer ?? "—"],
            ["Model", equipment.model ?? "—"],
            ["Serial number", equipment.serialNumber ?? "—"],
            ["Calibration cycle", equipment.calibrationIntervalDays ? `${equipment.calibrationIntervalDays} days` : "Not required"],
            ["Last successful calibration", formatDate(equipment.lastSuccessfulCalibrationAt)],
            ["Next due", formatDate(equipment.dueDate)],
            ...(equipment.deletedAt ? [["Deleted at", formatDateTime(equipment.deletedAt)]] : []),
          ].map(([label, value]) => (
            <Box key={label}>
              <Typography variant="caption" color="text.secondary">{label}</Typography>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{value}</Typography>
            </Box>
          ))}
        </Box>

        <Divider />
        <Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", pt: 2 }}>
          <Typography variant="h2">Technical history</Typography>
          <Chip size="small" label={`${history.length} entries · ${correctionCount} corrections`} />
        </Stack>
        {history.length === 0 ? (
          <Alert severity="info" sx={{ mt: 1.5 }}>No technical events have been recorded.</Alert>
        ) : (
          <List disablePadding sx={{ mt: 1 }}>
            {history.map((event) => {
              const correction = equipment.events.find((candidate) => candidate.correctsEventId === event.id);
              const corrected = Boolean(correction);
              const canCorrect = !equipment.deletedAt && !corrected && (event.type === "CALIBRATION" || event.type === "MAINTENANCE");
              return (
                <ListItem key={event.id} disableGutters alignItems="flex-start" sx={{ borderBottom: "1px solid", borderColor: "divider", py: 1.25 }}>
                  <ListItemIcon sx={{ minWidth: 36, color: corrected ? "text.disabled" : "primary.main", mt: 0.25 }}><EventIcon event={event} /></ListItemIcon>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                      <Typography sx={{ fontSize: "0.88rem", fontWeight: 650, color: corrected ? "text.secondary" : "text.primary", textDecoration: corrected ? "line-through" : "none" }}>
                        {eventLabels[event.type]}
                      </Typography>
                      {corrected ? <Chip size="small" label="Corrected" variant="outlined" /> : null}
                    </Stack>
                    <Typography color="text.secondary" sx={{ fontSize: "0.78rem" }}>
                      {`${formatDateTime(event.occurredAt)}${event.type === "CALIBRATION" ? event.successful ? " · Passed" : " · Failed" : ""}${event.note ? ` · ${event.note}` : ""}`}
                    </Typography>
                    {correction ? (
                      <Box sx={{ mt: 1, px: 1.25, py: 1, bgcolor: "#fff8e8", borderLeft: "3px solid", borderColor: "warning.main", borderRadius: "0 4px 4px 0" }}>
                        <Typography sx={{ fontSize: "0.76rem", fontWeight: 750, color: "warning.dark" }}>Correction · {formatDateTime(correction.occurredAt)}</Typography>
                        <Typography color="text.secondary" sx={{ fontSize: "0.78rem", overflowWrap: "anywhere" }}>{correction.note}</Typography>
                      </Box>
                    ) : null}
                  </Box>
                  {canCorrect ? <Button size="small" onClick={() => onCorrect(event)}>Correct entry</Button> : null}
                </ListItem>
              );
            })}
          </List>
        )}

        {equipment.deletedAt ? (
          <Button fullWidth variant="contained" startIcon={<RestoreOutlined />} onClick={onRestoreDeleted} sx={{ mt: 3 }}>Restore to inventory</Button>
        ) : (
          <>
            <Button fullWidth variant="outlined" color={equipment.archivedAt ? "primary" : "warning"} startIcon={equipment.archivedAt ? <RestoreOutlined /> : <ArchiveOutlined />} onClick={onChangeState} sx={{ mt: 3 }}>
              {equipment.archivedAt ? "Return to service" : "Remove from service"}
            </Button>
            <Button fullWidth color="error" startIcon={<DeleteOutlineOutlined />} onClick={onDelete} sx={{ mt: 1 }}>Delete from inventory</Button>
          </>
        )}
      </Box>
    </Drawer>
  );
}
