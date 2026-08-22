import { type FormEvent, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";

import {
  addEquipmentEvent,
  archiveEquipment,
  correctEquipmentEvent,
  createEquipment,
  deleteEquipment,
  restoreDeletedEquipment,
  restoreEquipment,
  updateEquipment,
  type CreateEquipmentInput,
  type Equipment,
  type EquipmentEvent,
  type UpdateEquipmentInput,
} from "./equipment.js";

type SavedHandler = (equipment: Equipment, message: string) => void;

interface DialogProps {
  equipment: Equipment;
  open: boolean;
  onClose: () => void;
  onSaved: SavedHandler;
}

export interface EquipmentFormDialogProps extends Omit<DialogProps, "equipment"> {
  mode: "create" | "edit";
  equipment?: Equipment;
}

export function EquipmentFormDialog({ mode, equipment, open, onClose, onSaved }: EquipmentFormDialogProps) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const data = new FormData(event.currentTarget);
    const interval = String(data.get("calibrationIntervalDays") ?? "").trim();
    const common = {
      name: String(data.get("name") ?? ""),
      category: String(data.get("category") ?? ""),
      manufacturer: String(data.get("manufacturer") ?? "") || null,
      model: String(data.get("model") ?? "") || null,
      serialNumber: String(data.get("serialNumber") ?? "") || null,
      location: String(data.get("location") ?? ""),
      calibrationIntervalDays: interval ? Number(interval) : null,
    };

    try {
      if (mode === "create") {
        const created = await createEquipment({
          ...common,
          assetTag: String(data.get("assetTag") ?? ""),
          manufacturer: common.manufacturer ?? undefined,
          model: common.model ?? undefined,
          serialNumber: common.serialNumber ?? undefined,
          calibrationIntervalDays: common.calibrationIntervalDays ?? undefined,
        } satisfies CreateEquipmentInput);
        onSaved(created, `${created.assetTag} was added to the inventory.`);
      } else if (equipment) {
        const updated = await updateEquipment(equipment.id, common satisfies UpdateEquipmentInput);
        onSaved(updated, `${updated.assetTag} was updated.`);
      }
      onClose();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not save equipment.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="md">
      <form onSubmit={submit}>
        <DialogTitle>{mode === "create" ? "Add equipment" : `Edit ${equipment?.assetTag ?? "equipment"}`}</DialogTitle>
        <DialogContent dividers>
          {error ? <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert> : null}
          <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" }, gap: 2 }}>
            {mode === "create" ? <TextField name="assetTag" label="Asset tag" required slotProps={{ htmlInput: { maxLength: 50 } }} placeholder="OSC-002" /> : null}
            <TextField name="name" label="Equipment name" required defaultValue={equipment?.name} slotProps={{ htmlInput: { maxLength: 120 } }} />
            <TextField name="category" label="Category" required defaultValue={equipment?.category} slotProps={{ htmlInput: { maxLength: 80 } }} />
            <TextField name="location" label="Location" required defaultValue={equipment?.location} slotProps={{ htmlInput: { maxLength: 100 } }} />
            <TextField name="manufacturer" label="Manufacturer" defaultValue={equipment?.manufacturer ?? ""} slotProps={{ htmlInput: { maxLength: 100 } }} />
            <TextField name="model" label="Model" defaultValue={equipment?.model ?? ""} slotProps={{ htmlInput: { maxLength: 100 } }} />
            <TextField name="serialNumber" label="Serial number" defaultValue={equipment?.serialNumber ?? ""} slotProps={{ htmlInput: { maxLength: 100 } }} />
            <TextField
              name="calibrationIntervalDays"
              label="Calibration interval (days)"
              type="number"
              defaultValue={equipment?.calibrationIntervalDays ?? ""}
              slotProps={{ htmlInput: { min: 1, step: 1 } }}
              helperText="Leave blank when calibration is not required. Record the last calibration as a technical event after saving."
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={saving}>{saving ? "Saving…" : "Save equipment"}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export function EventDialog({ equipment, open, onClose, onSaved }: DialogProps) {
  const [type, setType] = useState<"CALIBRATION" | "MAINTENANCE">("CALIBRATION");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const data = new FormData(event.currentTarget);
    try {
      const updated = await addEquipmentEvent(equipment.id, {
        type,
        occurredAt: new Date(String(data.get("occurredAt"))).toISOString(),
        successful: type === "CALIBRATION" ? data.get("successful") === "on" : undefined,
        note: String(data.get("note") ?? "") || undefined,
      });
      onSaved(updated, `${type === "CALIBRATION" ? "Calibration" : "Maintenance"} recorded for ${equipment.assetTag}.`);
      onClose();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not record event.");
    } finally {
      setSaving(false);
    }
  }

  const defaultDate = new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
      <form onSubmit={submit}>
        <DialogTitle>Record technical event</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2}>
            {error ? <Alert severity="error">{error}</Alert> : null}
            <FormControl fullWidth>
              <InputLabel id="event-type-label">Event type</InputLabel>
              <Select labelId="event-type-label" label="Event type" value={type} onChange={(event) => setType(event.target.value as typeof type)}>
                <MenuItem value="CALIBRATION">Calibration</MenuItem>
                <MenuItem value="MAINTENANCE">Maintenance</MenuItem>
              </Select>
            </FormControl>
            <TextField name="occurredAt" label="Occurred at" type="datetime-local" defaultValue={defaultDate} required slotProps={{ inputLabel: { shrink: true } }} />
            {type === "CALIBRATION" ? <FormControlLabel control={<Checkbox name="successful" defaultChecked />} label="Calibration passed" /> : null}
            <TextField name="note" label="Technical note" multiline minRows={3} slotProps={{ htmlInput: { maxLength: 500 } }} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={saving}>{saving ? "Recording…" : "Record event"}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export function StateDialog({ equipment, open, onClose, onSaved }: DialogProps) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const restoring = equipment.archivedAt !== null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const note = String(new FormData(event.currentTarget).get("note") ?? "") || undefined;
    try {
      const updated = restoring ? await restoreEquipment(equipment.id, note) : await archiveEquipment(equipment.id, note);
      onSaved(updated, `${equipment.assetTag} was ${restoring ? "returned to service" : "removed from service"}.`);
      onClose();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not change operational state.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
      <form onSubmit={submit}>
        <DialogTitle>{restoring ? "Return equipment to service?" : "Remove equipment from service?"}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2}>
            {error ? <Alert severity="error">{error}</Alert> : null}
            <Typography color="text.secondary">This preserves the equipment and appends an auditable event. No history will be deleted.</Typography>
            <TextField name="note" label="Reason or note" multiline minRows={3} slotProps={{ htmlInput: { maxLength: 500 } }} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>Cancel</Button>
          <Button type="submit" variant="contained" color={restoring ? "primary" : "warning"} disabled={saving}>
            {saving ? "Saving…" : restoring ? "Return to service" : "Remove from service"}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export interface CorrectionDialogProps extends DialogProps {
  event: EquipmentEvent;
}

export function CorrectionDialog({ equipment, event, open, onClose, onSaved }: CorrectionDialogProps) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(submitEvent: FormEvent<HTMLFormElement>) {
    submitEvent.preventDefault();
    setSaving(true);
    setError(null);
    const note = String(new FormData(submitEvent.currentTarget).get("note") ?? "");
    try {
      const updated = await correctEquipmentEvent(equipment.id, event.id, note);
      onSaved(updated, `Correction appended for ${event.type === "CALIBRATION" ? "Calibration" : "Maintenance"}.`);
      onClose();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not correct the event.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
      <form onSubmit={submit}>
        <DialogTitle>Correct history entry</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2}>
            {error ? <Alert severity="error">{error}</Alert> : null}
            <Alert severity="info">The original entry remains visible for audit purposes but will stop affecting equipment status.</Alert>
            <Typography variant="body2">
              {event.type === "CALIBRATION" ? "Calibration" : "Maintenance"} · {new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(event.occurredAt))}
            </Typography>
            <TextField name="note" label="Reason for correction" required multiline minRows={3} slotProps={{ htmlInput: { maxLength: 500 } }} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={saving}>{saving ? "Saving…" : "Append correction"}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export function DeleteEquipmentDialog({ equipment, open, onClose, onSaved }: DialogProps) {
  const [confirmation, setConfirmation] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (confirmation !== equipment.assetTag) return;
    setSaving(true);
    setError(null);
    try {
      const note = String(new FormData(event.currentTarget).get("note") ?? "") || undefined;
      const updated = await deleteEquipment(equipment.id, note);
      onSaved(updated, `${equipment.assetTag} was moved to deleted records.`);
      onClose();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not delete the equipment.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
      <form onSubmit={submit}>
        <DialogTitle>Delete equipment from inventory?</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2}>
            {error ? <Alert severity="error">{error}</Alert> : null}
            <Alert severity="warning">This removes the equipment from the operational inventory. Its details and complete technical history remain in deleted records and it can be restored.</Alert>
            <Typography variant="body2">Type <strong>{equipment.assetTag}</strong> to confirm.</Typography>
            <TextField label="Asset tag confirmation" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} autoComplete="off" autoFocus />
            <TextField name="note" label="Reason or note" multiline minRows={2} slotProps={{ htmlInput: { maxLength: 500 } }} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>Cancel</Button>
          <Button type="submit" color="error" variant="contained" disabled={saving || confirmation !== equipment.assetTag}>
            {saving ? "Deleting…" : "Move to deleted records"}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export function RestoreDeletedDialog({ equipment, open, onClose, onSaved }: DialogProps) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const note = String(new FormData(event.currentTarget).get("note") ?? "") || undefined;
      const updated = await restoreDeletedEquipment(equipment.id, note);
      onSaved(updated, `${equipment.assetTag} was restored to the inventory.`);
      onClose();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not restore the equipment.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
      <form onSubmit={submit}>
        <DialogTitle>Restore equipment to inventory?</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2}>
            {error ? <Alert severity="error">{error}</Alert> : null}
            <Typography color="text.secondary">The equipment becomes operationally visible again. Its deletion and restoration remain in the history.</Typography>
            <TextField name="note" label="Reason or note" multiline minRows={2} slotProps={{ htmlInput: { maxLength: 500 } }} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={saving}>{saving ? "Restoring…" : "Restore to inventory"}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
