
import { useState } from 'react';
import type {
  CreateCoolingPointPayload
} from '../types';

type Props = {
  latitude: number;
  longitude: number;
  onSave: (data: CreateCoolingPointPayload) => Promise<void>;
  onCancel: () => void;
};

const categories = [
  ['TEA_STALL', 'Tea / Chai Stall'],
  ['SHOP', 'Local Shop'],
  ['PARK', 'Park / Garden'],
  ['WATER_POINT', 'Drinking Water Point'],
  ['BUS_STOP', 'Covered Bus Stop'],
  ['TREE_SHADE', 'Tree Shade'],
  ['REST_AREA', 'Rest Area'],
  ['INDOOR', 'Indoor Cooling Space'],
  ['OTHER', 'Other']
];

export default function CoolingPointForm({
  latitude,
  longitude,
  onSave,
  onCancel
}: Props) {

  const [name, setName] = useState('');
  const [category, setCategory] = useState('TEA_STALL');
  const [shadeScore, setShadeScore] = useState(70);
  const [shadeType, setShadeType] = useState('ROOF');
  const [seating, setSeating] = useState(true);
  const [water, setWater] = useState(false);
  const [openingHours, setOpeningHours] = useState('');
  const [description, setDescription] = useState('');
  const [submittedBy, setSubmittedBy] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (!name.trim()) {
      setError('Please enter the place name.');
      return;
    }

    setSaving(true);
    setError('');

    try {
      await onSave({
        name: name.trim(),
        category,
        latitude,
        longitude,
        shadeScore,
        shadeType,
        seatingAvailable: seating,
        waterAvailable: water,
        openingHours,
        description,
        submittedBy
      });
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to save place.'
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="cooling-form-backdrop">
      <form
        className="cooling-form"
        onSubmit={handleSubmit}
      >
        <div className="cooling-form-header">
          <div>
            <span className="section-index">
              COMMUNITY CONTRIBUTION
            </span>
            <h2>Add Cooling Point</h2>
          </div>

          <button type="button" onClick={onCancel}>
            ✕
          </button>
        </div>

        <label>Place name *</label>
        <input
          value={name}
          onChange={e => setName(e.target.value)}
          placeholder="e.g. Sharma Chai Corner"
          maxLength={128}
          required
        />

        <label>Category</label>
        <select
          value={category}
          onChange={e => setCategory(e.target.value)}
        >
          {categories.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>

        <label>Shade availability score: {shadeScore}/100</label>
        <input
          type="range"
          min="0"
          max="100"
          value={shadeScore}
          onChange={e => setShadeScore(Number(e.target.value))}
        />

        <label>Type of shade</label>
        <select
          value={shadeType}
          onChange={e => setShadeType(e.target.value)}
        >
          <option value="ROOF">Permanent roof</option>
          <option value="TIN_SHED">Tin shed</option>
          <option value="TREE">Tree shade</option>
          <option value="UMBRELLA">Umbrella / Canopy</option>
          <option value="INDOOR">Indoor</option>
          <option value="NONE">No shade</option>
          <option value="UNKNOWN">Not sure</option>
        </select>

        <label className="cooling-check">
          <input
            type="checkbox"
            checked={seating}
            onChange={e => setSeating(e.target.checked)}
          />
          Seating available
        </label>

        <label className="cooling-check">
          <input
            type="checkbox"
            checked={water}
            onChange={e => setWater(e.target.checked)}
          />
          Drinking water available
        </label>

        <label>Opening hours</label>
        <input
          value={openingHours}
          onChange={e => setOpeningHours(e.target.value)}
          placeholder="e.g. 7 AM - 9 PM"
          maxLength={128}
        />

        <label>Description</label>
        <textarea
          value={description}
          onChange={e => setDescription(e.target.value)}
          placeholder="Describe the shade, seating or facilities..."
          maxLength={255}
          rows={3}
        />

        <label>Your contributor name (optional)</label>
        <input
          value={submittedBy}
          onChange={e => setSubmittedBy(e.target.value)}
          placeholder="Your name"
          maxLength={100}
        />

        <div className="cooling-coordinates">
          Selected coordinates:
          <br />
          {latitude.toFixed(6)}, {longitude.toFixed(6)}
        </div>

        {error && (
          <p className="cooling-error">{error}</p>
        )}

        <div className="cooling-form-actions">
          <button
            type="button"
            onClick={onCancel}
            disabled={saving}
          >
            Cancel
          </button>

          <button type="submit" disabled={saving}>
            {saving ? 'Saving...' : 'Submit Place'}
          </button>
        </div>

        <p className="cooling-disclaimer">
          Community submissions are marked pending verification.
        </p>
      </form>
    </div>
  );
}
