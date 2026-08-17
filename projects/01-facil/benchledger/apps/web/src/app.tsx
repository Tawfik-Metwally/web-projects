import { type FormEvent, useEffect, useMemo, useState } from "react";
import {
  AddOutlined,
  ArchiveOutlined,
  BuildOutlined,
  CheckCircleOutlined,
  EditOutlined,
  Inventory2Outlined,
  RefreshOutlined,
  RestoreOutlined,
  ScienceOutlined,
  SearchOutlined,
  WarningAmberOutlined,
} from "@mui/icons-material";
import {
  Alert,
  AppBar,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Drawer,
  FormControl,
  FormControlLabel,
  IconButton,
  InputAdornment,
  InputLabel,
  List,
  ListItem,
  ListItemIcon,
  MenuItem,
  Paper,
  Select,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Toolbar,
  Tooltip,
  Typography,
} from "@mui/material";

import {
  addEquipmentEvent,
  archiveEquipment,
  correctEquipmentEvent,
  createEquipment,
  listEquipment,
  restoreEquipment,
  updateEquipment,
  type CreateEquipmentInput,
  type Equipment,
  type EquipmentEvent,
  type EquipmentStatus,
  type UpdateEquipmentInput,
} from "./equipment.js";

interface HealthResponse {
  status: "ok";
  database: "reachable";
}

type ConnectionState = "checking" | "ready" | "unavailable";
type StatusFilter = "ALL" | EquipmentStatus;

const apiUrl = import.meta.env.VITE_API_URL ?? "http://localhost:3000/api";

const statusMeta: Record<EquipmentStatus, { label: string; color: "success" | "warning" | "error" | "default"; description: string }> = {
  ACTIVE: { label: "Active", color: "success", description: "Available for use and not inside the calibration warning window." },
  DUE_SOON: { label: "Due soon", color: "warning", description: "Calibration is due within 30 days." },
  OVERDUE: { label: "Overdue", color: "error", description: "Calibration is required and no current successful calibration exists." },
  CALIBRATION_FAILED: { label: "Calibration failed", color: "error", description: "The latest effective calibration failed; use should be restricted until it passes." },
  OUT_OF_SERVICE: { label: "Out of service", color: "default", description: "The equipment was explicitly removed from operational use." },
};

const eventLabels: Record<EquipmentEvent["type"], string> = {
  CALIBRATION: "Calibration",
  MAINTENANCE: "Maintenance",
  OUT_OF_SERVICE: "Removed from service",
  RETURNED_TO_SERVICE: "Returned to service",
  CORRECTION: "Correction recorded",
};

function formatDate(value: string | null): string {
  if (!value) return "Not available";
  const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(value);
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    ...(dateOnly ? { timeZone: "UTC" } : {}),
  }).format(new Date(value));
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function StatusChip({ status }: { status: EquipmentStatus }) {
  const meta = statusMeta[status];
  return <Tooltip title={meta.description}><Chip label={meta.label} color={meta.color} size="small" variant={status === "OUT_OF_SERVICE" ? "outlined" : "filled"} /></Tooltip>;
}

function Metric({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <Paper variant="outlined" sx={{ p: 1.75, minWidth: 0, borderTop: `3px solid ${tone ?? "#17324d"}` }}>
      <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.05em" }}>
        {label}
      </Typography>
      <Typography sx={{ mt: 0.25, fontSize: "1.75rem", lineHeight: 1.1, fontWeight: 750 }}>{value}</Typography>
    </Paper>
  );
}

interface EquipmentFormDialogProps {
  mode: "create" | "edit";
  equipment?: Equipment;
  open: boolean;
  onClose: () => void;
  onSaved: (equipment: Equipment, message: string) => void;
}

function EquipmentFormDialog({ mode, equipment, open, onClose, onSaved }: EquipmentFormDialogProps) {
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

interface EventDialogProps {
  equipment: Equipment;
  open: boolean;
  onClose: () => void;
  onSaved: (equipment: Equipment, message: string) => void;
}

function EventDialog({ equipment, open, onClose, onSaved }: EventDialogProps) {
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
      onSaved(updated, `${eventLabels[type]} recorded for ${equipment.assetTag}.`);
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

interface StateDialogProps {
  equipment: Equipment;
  open: boolean;
  onClose: () => void;
  onSaved: (equipment: Equipment, message: string) => void;
}

function StateDialog({ equipment, open, onClose, onSaved }: StateDialogProps) {
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
            <Typography color="text.secondary">
              This preserves the equipment and appends an auditable event. No history will be deleted.
            </Typography>
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

interface CorrectionDialogProps {
  equipment: Equipment;
  event: EquipmentEvent;
  open: boolean;
  onClose: () => void;
  onSaved: (equipment: Equipment, message: string) => void;
}

function CorrectionDialog({ equipment, event, open, onClose, onSaved }: CorrectionDialogProps) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(submitEvent: FormEvent<HTMLFormElement>) {
    submitEvent.preventDefault();
    setSaving(true);
    setError(null);
    const note = String(new FormData(submitEvent.currentTarget).get("note") ?? "");
    try {
      const updated = await correctEquipmentEvent(equipment.id, event.id, note);
      onSaved(updated, `Correction appended for ${eventLabels[event.type]}.`);
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
            <Alert severity="info">
              The original entry remains visible for audit purposes but will stop affecting equipment status.
            </Alert>
            <Typography variant="body2">
              {eventLabels[event.type]} · {formatDateTime(event.occurredAt)}
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

function EventIcon({ event }: { event: EquipmentEvent }) {
  if (event.type === "CALIBRATION") return <ScienceOutlined fontSize="small" />;
  if (event.type === "MAINTENANCE") return <BuildOutlined fontSize="small" />;
  if (event.type === "RETURNED_TO_SERVICE") return <RestoreOutlined fontSize="small" />;
  if (event.type === "CORRECTION") return <EditOutlined fontSize="small" />;
  return <ArchiveOutlined fontSize="small" />;
}

export function App() {
  const [connection, setConnection] = useState<ConnectionState>("checking");
  const [equipment, setEquipment] = useState<Equipment[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [formMode, setFormMode] = useState<"create" | "edit" | null>(null);
  const [eventOpen, setEventOpen] = useState(false);
  const [stateOpen, setStateOpen] = useState(false);
  const [correctionEvent, setCorrectionEvent] = useState<EquipmentEvent | null>(null);
  const [notice, setNotice] = useState<{ severity: "success" | "error"; text: string } | null>(null);

  const selected = equipment.find((item) => item.id === selectedId);
  const selectedHistory = selected?.events.filter((event) => event.type !== "CORRECTION") ?? [];
  const selectedCorrectionCount = (selected?.events.length ?? 0) - selectedHistory.length;

  async function load() {
    setLoading(true);
    try {
      const [healthResponse, equipmentList] = await Promise.all([fetch(`${apiUrl}/health`), listEquipment()]);
      if (!healthResponse.ok) throw new Error(`Health check failed with status ${healthResponse.status}`);
      const health = (await healthResponse.json()) as HealthResponse;
      setConnection(health.status === "ok" ? "ready" : "unavailable");
      setEquipment(equipmentList);
    } catch {
      setConnection("unavailable");
      setNotice({ severity: "error", text: "Could not connect to the BenchLedger services." });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  function acceptSaved(saved: Equipment, text: string) {
    setEquipment((current) => {
      const exists = current.some((item) => item.id === saved.id);
      return exists ? current.map((item) => (item.id === saved.id ? saved : item)) : [saved, ...current];
    });
    setSelectedId(saved.id);
    setNotice({ severity: "success", text });
  }

  const filteredEquipment = useMemo(() => {
    const normalized = search.trim().toLowerCase();
    return equipment.filter((item) => {
      const matchesStatus = statusFilter === "ALL" || item.status === statusFilter;
      const matchesSearch = !normalized || [item.assetTag, item.name, item.category, item.location, item.serialNumber ?? ""]
        .some((value) => value.toLowerCase().includes(normalized));
      return matchesStatus && matchesSearch;
    });
  }, [equipment, search, statusFilter]);

  const counts = useMemo(() => ({
    total: equipment.length,
    active: equipment.filter((item) => item.status === "ACTIVE").length,
    attention: equipment.filter((item) => item.status === "DUE_SOON").length,
    overdue: equipment.filter((item) => item.status === "OVERDUE").length,
    failed: equipment.filter((item) => item.status === "CALIBRATION_FAILED").length,
    outOfService: equipment.filter((item) => item.status === "OUT_OF_SERVICE").length,
  }), [equipment]);

  return (
    <Box sx={{ minHeight: "100vh" }}>
      <AppBar position="static" color="primary" elevation={0}>
        <Toolbar sx={{ minHeight: 58 }}>
          <Inventory2Outlined sx={{ mr: 1.25 }} />
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h1" color="inherit">BenchLedger</Typography>
            <Typography variant="caption" sx={{ color: "rgba(255,255,255,.72)", display: { xs: "none", sm: "block" } }}>Laboratory equipment operations</Typography>
          </Box>
          <Chip
            size="small"
            icon={connection === "ready" ? <CheckCircleOutlined /> : connection === "checking" ? <CircularProgress size={14} /> : <WarningAmberOutlined />}
            label={connection === "ready" ? "Services online" : connection === "checking" ? "Connecting" : "Services unavailable"}
            sx={{ bgcolor: connection === "ready" ? "#d7f0e2" : "#fff1d6", color: "#17324d", "& .MuiChip-icon": { color: "inherit" } }}
          />
        </Toolbar>
      </AppBar>

      <Box component="main" sx={{ width: "min(1440px, calc(100% - 32px))", mx: "auto", py: 3 }}>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ justifyContent: "space-between", alignItems: { xs: "stretch", sm: "center" }, mb: 2.5 }}>
          <Box>
            <Typography variant="h2">Equipment inventory</Typography>
            <Typography variant="body2" color="text.secondary">Operational state, calibration exposure and technical history.</Typography>
          </Box>
          <Button variant="contained" startIcon={<AddOutlined />} onClick={() => setFormMode("create")} disabled={connection !== "ready"}>
            Add equipment
          </Button>
        </Stack>

        <Box sx={{ display: "grid", gridTemplateColumns: { xs: "repeat(2, 1fr)", md: "repeat(3, 1fr)", lg: "repeat(6, 1fr)" }, gap: 1.5, mb: 2 }}>
          <Metric label="Inventory" value={counts.total} />
          <Metric label="Active" value={counts.active} tone="#24734f" />
          <Metric label="Due soon" value={counts.attention} tone="#b45309" />
          <Metric label="Overdue" value={counts.overdue} tone="#b42318" />
          <Metric label="Calibration failed" value={counts.failed} tone="#7f1d1d" />
          <Metric label="Out of service" value={counts.outOfService} tone="#59636e" />
        </Box>

        <Paper variant="outlined">
          <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} sx={{ p: 1.5 }}>
            <TextField
              size="small"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search asset, name, category or location"
              sx={{ flex: 1, minWidth: 240 }}
              slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchOutlined fontSize="small" /></InputAdornment> } }}
            />
            <FormControl size="small" sx={{ minWidth: 180 }}>
              <InputLabel id="status-filter-label">Operational status</InputLabel>
              <Select labelId="status-filter-label" value={statusFilter} label="Operational status" onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}>
                <MenuItem value="ALL">All statuses</MenuItem>
                {Object.entries(statusMeta).map(([value, meta]) => <MenuItem key={value} value={value}>{meta.label}</MenuItem>)}
              </Select>
            </FormControl>
            <Tooltip title="Refresh inventory">
              <IconButton onClick={() => void load()} disabled={loading} sx={{ alignSelf: { xs: "flex-end", md: "center" }, border: "1px solid", borderColor: "divider", borderRadius: 1 }}>
                <RefreshOutlined fontSize="small" />
              </IconButton>
            </Tooltip>
          </Stack>
          <Divider />
          <TableContainer>
            <Table size="small" aria-label="Equipment inventory">
              <TableHead>
                <TableRow>
                  <TableCell>Asset</TableCell>
                  <TableCell>Equipment</TableCell>
                  <TableCell sx={{ display: { xs: "none", sm: "table-cell" } }}>Location</TableCell>
                  <TableCell sx={{ display: { xs: "none", md: "table-cell" } }}>Calibration due</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loading ? (
                  <TableRow><TableCell colSpan={5} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell></TableRow>
                ) : filteredEquipment.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} align="center" sx={{ py: 7 }}>
                      <Inventory2Outlined color="disabled" sx={{ fontSize: 40, mb: 1 }} />
                      <Typography sx={{ fontWeight: 650 }}>No equipment matches this view</Typography>
                      <Typography variant="body2" color="text.secondary">Adjust the filters or add a new record.</Typography>
                    </TableCell>
                  </TableRow>
                ) : filteredEquipment.map((item) => (
                  <TableRow key={item.id} hover selected={item.id === selectedId} onClick={() => setSelectedId(item.id)} sx={{ cursor: "pointer" }}>
                    <TableCell><Typography component="span" sx={{ fontFamily: "ui-monospace, Consolas, monospace", fontSize: "0.78rem", fontWeight: 750, color: "primary.main" }}>{item.assetTag}</Typography></TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 650 }}>{item.name}</Typography>
                      <Typography variant="caption" color="text.secondary">{item.category}</Typography>
                    </TableCell>
                    <TableCell sx={{ display: { xs: "none", sm: "table-cell" } }}>{item.location}</TableCell>
                    <TableCell sx={{ display: { xs: "none", md: "table-cell" } }}>{item.calibrationIntervalDays ? item.dueDate ? formatDate(item.dueDate) : "No successful calibration" : "Not required"}</TableCell>
                    <TableCell><StatusChip status={item.status} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <Box sx={{ px: 2, py: 1.25, bgcolor: "#f8f9fa", borderTop: "1px solid", borderColor: "divider" }}>
            <Typography variant="caption" color="text.secondary">{filteredEquipment.length} of {equipment.length} records shown</Typography>
          </Box>
        </Paper>
      </Box>

      <Drawer anchor="right" open={Boolean(selected)} onClose={() => setSelectedId(null)} slotProps={{ paper: { sx: { width: { xs: "100%", sm: 440 } } } }}>
        {selected ? (
          <Box sx={{ p: 2.5 }}>
            <Stack direction="row" spacing={2} sx={{ justifyContent: "space-between", alignItems: "flex-start" }}>
              <Box>
                <Typography sx={{ fontFamily: "ui-monospace, Consolas, monospace", color: "primary.main", fontSize: "0.78rem", fontWeight: 750 }}>{selected.assetTag}</Typography>
                <Typography variant="h2" sx={{ mt: 0.5 }}>{selected.name}</Typography>
              </Box>
              <StatusChip status={selected.status} />
            </Stack>
            <Stack direction="row" spacing={1} sx={{ my: 2 }}>
              <Button size="small" variant="outlined" startIcon={<EditOutlined />} onClick={() => setFormMode("edit")}>Edit</Button>
              <Button size="small" variant="outlined" startIcon={<BuildOutlined />} onClick={() => setEventOpen(true)}>Record event</Button>
            </Stack>
            <Divider />
            <Box sx={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 2, py: 2.25 }}>
              {[
                ["Category", selected.category],
                ["Location", selected.location],
                ["Manufacturer", selected.manufacturer ?? "—"],
                ["Model", selected.model ?? "—"],
                ["Serial number", selected.serialNumber ?? "—"],
                ["Calibration cycle", selected.calibrationIntervalDays ? `${selected.calibrationIntervalDays} days` : "Not required"],
                ["Last successful calibration", formatDate(selected.lastSuccessfulCalibrationAt)],
                ["Next due", formatDate(selected.dueDate)],
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
              <Chip size="small" label={`${selectedHistory.length} entries · ${selectedCorrectionCount} corrections`} />
            </Stack>
            {selectedHistory.length === 0 ? (
              <Alert severity="info" sx={{ mt: 1.5 }}>No technical events have been recorded.</Alert>
            ) : (
              <List disablePadding sx={{ mt: 1 }}>
                {selectedHistory.map((event) => {
                  const correction = selected.events.find((candidate) => candidate.correctsEventId === event.id);
                  const corrected = Boolean(correction);
                  const canCorrect = !corrected && (event.type === "CALIBRATION" || event.type === "MAINTENANCE");
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
                            <Typography sx={{ fontSize: "0.76rem", fontWeight: 750, color: "warning.dark" }}>
                              Correction · {formatDateTime(correction.occurredAt)}
                            </Typography>
                            <Typography color="text.secondary" sx={{ fontSize: "0.78rem", overflowWrap: "anywhere" }}>
                              {correction.note}
                            </Typography>
                          </Box>
                        ) : null}
                      </Box>
                      {canCorrect ? <Button size="small" onClick={() => setCorrectionEvent(event)}>Correct entry</Button> : null}
                    </ListItem>
                  );
                })}
              </List>
            )}
            <Button
              fullWidth
              variant="outlined"
              color={selected.archivedAt ? "primary" : "warning"}
              startIcon={selected.archivedAt ? <RestoreOutlined /> : <ArchiveOutlined />}
              onClick={() => setStateOpen(true)}
              sx={{ mt: 3 }}
            >
              {selected.archivedAt ? "Return to service" : "Remove from service"}
            </Button>
          </Box>
        ) : null}
      </Drawer>

      {formMode ? (
        <EquipmentFormDialog
          key={`${formMode}-${selected?.id ?? "new"}`}
          mode={formMode}
          equipment={formMode === "edit" ? selected : undefined}
          open
          onClose={() => setFormMode(null)}
          onSaved={acceptSaved}
        />
      ) : null}
      {selected && eventOpen ? <EventDialog key={selected.id} equipment={selected} open onClose={() => setEventOpen(false)} onSaved={acceptSaved} /> : null}
      {selected && stateOpen ? <StateDialog key={`${selected.id}-${selected.archivedAt ?? "active"}`} equipment={selected} open onClose={() => setStateOpen(false)} onSaved={acceptSaved} /> : null}
      {selected && correctionEvent ? <CorrectionDialog key={correctionEvent.id} equipment={selected} event={correctionEvent} open onClose={() => setCorrectionEvent(null)} onSaved={acceptSaved} /> : null}

      <Snackbar open={Boolean(notice)} autoHideDuration={4500} onClose={() => setNotice(null)} anchorOrigin={{ vertical: "bottom", horizontal: "center" }}>
        {notice ? <Alert severity={notice.severity} variant="filled" onClose={() => setNotice(null)}>{notice.text}</Alert> : undefined}
      </Snackbar>
    </Box>
  );
}
