import { useEffect, useMemo, useState, useRef } from 'react';
import CoolingPointForm from './components/CoolingPointForm';

import {
  findRoutes,
  getLocations,
  getWeather,
  searchPlaces,
  reverseGeocode,
  addCoolingPoint,
  getCommunityCoolingPoints
} from './api';

import type {
  Location,
  Profile,
  RouteResult,
  WeatherInfo,
  SearchResult,
  CoolingPoint
} from './types';

import {
  Activity,
  Clock3,
  Compass,
  Flame,
  Footprints,
  Info,
  LocateFixed,
  MapPin,
  Navigation,
  ShieldCheck,
  SlidersHorizontal,
  Sun,
  TreePine,
  Zap,
  CloudSun,
  X,
  Search
} from 'lucide-react';

import MapView from './components/MapView';

const profiles: { id: Profile; label: string; desc: string }[] = [
  { id: 'balanced', label: 'Balanced', desc: 'A considered mix' },
  { id: 'safety', label: 'Safety priority', desc: 'Visibility & hazard proxies' },
  { id: 'heat', label: 'Heat protection', desc: 'Shade & cooling access' },
  { id: 'quick', label: 'Quick walk', desc: 'Time weighted higher' }
];

const PRESETS = [
  {
    label: 'Bhopal: Station → Library',
    origin: 'Bhopal Junction',
    originCoords: { lat: 23.2599, lng: 77.4126 },
    dest: 'Central Library Bhopal',
    destCoords: { lat: 23.2642, lng: 77.4155 }
  },
  {
    label: 'Bhopal: Lake → Van Vihar',
    origin: 'Upper Lake Bhopal',
    originCoords: { lat: 23.2450, lng: 77.3850 },
    dest: 'Van Vihar National Park',
    destCoords: { lat: 23.2300, lng: 77.3600 }
  },
  {
    label: 'Bhopal: TT Nagar → Science Centre',
    origin: 'TT Nagar Stadium',
    originCoords: { lat: 23.2380, lng: 77.4010 },
    dest: 'Regional Science Centre',
    destCoords: { lat: 23.2290, lng: 77.3980 }
  }
];

const fmt = (n: number) =>
  n >= 1000 ? `${(n / 1000).toFixed(1)} km` : `${Math.round(n)} m`;

export default function App() {
  const [coolingPoints, setCoolingPoints] = useState<CoolingPoint[]>([]);
  const [addingCoolingPoint, setAddingCoolingPoint] = useState(false);
  const [pickedCoolingPoint, setPickedCoolingPoint] =
    useState<{ lat: number; lng: number } | null>(null);

  const [locations, setLocations] = useState<Location[]>([]);

  // Origin and destination state
  const [origin, setOrigin] = useState('Bhopal Junction');
  const [originCoords, setOriginCoords] = useState<{
    lat: number;
    lng: number;
  } | null>({
    lat: 23.2599,
    lng: 77.4126
  });

  const [destination, setDestination] = useState('Central Library Bhopal');
  const [destCoords, setDestCoords] = useState<{
    lat: number;
    lng: number;
  } | null>({
    lat: 23.2642,
    lng: 77.4155
  });

  // Autocomplete states
  const [originSuggestions, setOriginSuggestions] = useState<SearchResult[]>([]);
  const [destSuggestions, setDestSuggestions] = useState<SearchResult[]>([]);
  const [showOriginMenu, setShowOriginMenu] = useState(false);
  const [showDestMenu, setShowDestMenu] = useState(false);

  const originInputRef = useRef<HTMLDivElement>(null);
  const destInputRef = useRef<HTMLDivElement>(null);

  // Preference and weather states
  const [profile, setProfile] = useState<Profile>('balanced');
  const [temp, setTemp] = useState(36);
  const [weather, setWeather] = useState<WeatherInfo | null>(null);
  const [result, setResult] = useState<RouteResult | null>(null);
  const [selected, setSelected] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [userLocation, setUserLocation] = useState<{
    lat: number;
    lng: number;
  } | null>(null);

  const [locationStatus, setLocationStatus] = useState(
    'Bhopal, MP (Default Demo Region)'
  );

  // Initial load
  useEffect(() => {
    getLocations()
      .then(setLocations)
      .catch((e) => setError(e.message));

    // Fetch initial weather for default Bhopal coordinates
    getWeather(23.2599, 77.4126)
      .then((w) => {
        setWeather(w);
        setTemp(Math.round(w.temperatureC));
      })
      .catch(() => {});
  }, []);

  // Close autocomplete menus when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        originInputRef.current &&
        !originInputRef.current.contains(event.target as Node)
      ) {
        setShowOriginMenu(false);
      }

      if (
        destInputRef.current &&
        !destInputRef.current.contains(event.target as Node)
      ) {
        setShowDestMenu(false);
      }
    }

    document.addEventListener('mousedown', handleClickOutside);

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  // Debounced search for origin
  useEffect(() => {
    if (!origin || origin.trim().length < 2 || !showOriginMenu) {
      setOriginSuggestions([]);
      return;
    }

    const timer = setTimeout(() => {
      searchPlaces(
        origin,
        userLocation?.lat || 23.2599,
        userLocation?.lng || 77.4126
      )
        .then((items) => {
          setOriginSuggestions(items);
        })
        .catch(() => setOriginSuggestions([]));
    }, 280);

    return () => clearTimeout(timer);
  }, [origin, showOriginMenu, userLocation]);

  // Debounced search for destination
  useEffect(() => {
    if (!destination || destination.trim().length < 2 || !showDestMenu) {
      setDestSuggestions([]);
      return;
    }

    const timer = setTimeout(() => {
      searchPlaces(
        destination,
        userLocation?.lat || 23.2599,
        userLocation?.lng || 77.4126
      )
        .then((items) => {
          setDestSuggestions(items);
        })
        .catch(() => setDestSuggestions([]));
    }, 280);

    return () => clearTimeout(timer);
  }, [destination, showDestMenu, userLocation]);

  const active = useMemo(
    () =>
      result?.routes.find((r) => r.id === selected) || result?.routes[0],
    [result, selected]
  );

  async function useMyLocation() {
    if (!navigator.geolocation) {
      setLocationStatus('Geolocation is not supported by your browser.');
      return;
    }

    setLocationStatus('Acquiring GPS location...');

    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const lat = position.coords.latitude;
        const lng = position.coords.longitude;

        setUserLocation({ lat, lng });
        setOriginCoords({ lat, lng });

        try {
          const rev = await reverseGeocode(lat, lng);

          const resolvedName =
            rev.name ||
            rev.displayName ||
            `GPS (${lat.toFixed(4)}, ${lng.toFixed(4)})`;

          setOrigin(resolvedName);
          setLocationStatus(`Location detected: ${resolvedName}`);
        } catch {
          const fallbackName =
            `Current Location (${lat.toFixed(4)}, ${lng.toFixed(4)})`;

          setOrigin(fallbackName);
          setLocationStatus(`Location detected: ${fallbackName}`);
        }

        try {
          const weatherData = await getWeather(lat, lng);
          setWeather(weatherData);
          setTemp(Math.round(weatherData.temperatureC));
        } catch {
          // Keep the previously loaded weather value as fallback.
        }
      },
      (geoError) => {
        setLocationStatus(`GPS error: ${geoError.message}`);
      },
      {
        enableHighAccuracy: true,
        timeout: 12000,
        maximumAge: 30000
      }
    );
  }

  async function submit(customProfile?: Profile) {
    if (!origin.trim()) {
      setError('Please specify a starting location.');
      return;
    }

    if (!destination.trim()) {
      setError('Please specify a destination.');
      return;
    }

    if (origin.trim().toLowerCase() === destination.trim().toLowerCase()) {
      setError('Origin and destination must be different locations.');
      return;
    }

    setLoading(true);
    setError('');

    const activeProfile = customProfile || profile;

    try {
      const r = await findRoutes({
        origin: origin.trim(),
        destination: destination.trim(),
        profile: activeProfile,
        temperature_c: temp,
        originLat: originCoords?.lat,
        originLng: originCoords?.lng,
        destLat: destCoords?.lat,
        destLng: destCoords?.lng
      });

      setResult(r);

      const recommendedId =
        r.recommended_route_id ||
        r.routes.find((x) => x.recommended)?.id ||
        r.routes[0]?.id;

      setSelected(recommendedId || '');
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : 'Unexpected error analyzing pedestrian routes.'
      );
    } finally {
      setLoading(false);
    }
  }

  // Load community cooling points around the current route.
  useEffect(() => {
    const lat =
      active?.coordinates?.[0]?.[0] ??
      originCoords?.lat ??
      23.2599;

    const lng =
      active?.coordinates?.[0]?.[1] ??
      originCoords?.lng ??
      77.4126;

    getCommunityCoolingPoints(lat, lng, 20000)
      .then(setCoolingPoints)
      .catch((error) => {
        console.error(
          'Could not load community cooling points:',
          error
        );
      });
  }, [result, originCoords]);

  // Load default route on initial start
  useEffect(() => {
    if (!result && origin && destination) {
      submit();
    }
  }, []);

  // When profile is switched, re-evaluate routes if results exist
  function handleProfileSelect(p: Profile) {
    setProfile(p);

    if (result) {
      submit(p);
    }
  }

  function applyPreset(preset: (typeof PRESETS)[0]) {
    setOrigin(preset.origin);
    setOriginCoords(preset.originCoords);

    setDestination(preset.dest);
    setDestCoords(preset.destCoords);

    setShowOriginMenu(false);
    setShowDestMenu(false);

    setLocationStatus(`Preset selected: ${preset.label}`);
  }

  async function handleCoolingPointSave(
    data: import('./types').CreateCoolingPointPayload
  ) {
    const saved = await addCoolingPoint(data);

    setCoolingPoints((previous) => [
      ...previous,
      saved
    ]);

    setPickedCoolingPoint(null);
    setAddingCoolingPoint(false);
    setError('');

    alert(
      'Cooling point submitted successfully! It is pending verification.'
    );
  }

  return (
    <div className="app-shell">
      <aside className="rail">
        <div className="brand-mark">
          <Navigation size={20} />
        </div>

        <div className="rail-line" />

        <div className="rail-bottom">
          SR
          <br />
          <span>01 / 04</span>
        </div>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div className="brand">
            <span className="brand-name">
              safe<span>route</span>
            </span>

            <span className="brand-tag">
              URBAN WALK INTELLIGENCE · MARGNOVA
            </span>
          </div>

          <div className="top-right">
            {weather && (
              <span className="weather-pill" title={weather.disclaimer}>
                <CloudSun
                  size={15}
                  style={{
                    verticalAlign: 'middle',
                    marginRight: 4
                  }}
                />
                LIVE: {Math.round(weather.temperatureC)}°C (
                {weather.condition}) · {weather.dataTrust}
              </span>
            )}

            <span className="live-dot" /> LOCAL DEV
            <div className="avatar">SR</div>
          </div>
        </header>

        <section className="hero">
          <div>
            <div className="eyebrow">
              <span className="eyebrow-line" /> WALK WITH CONTEXT
            </div>

            <h1>
              Better paths.
              <br />
              <em>More thoughtful</em> walks.
            </h1>

            <p className="hero-copy">
              A real-world pedestrian navigation system, balancing walking
              time, heat exposure, tree canopy shade, and personal safety
              priorities.
            </p>
          </div>

          <div className="hero-stamp">
            <Compass size={28} />

            <span>
              ROUTE
              <br />
              INTELLIGENCE
            </span>

            <b>v1.0</b>
          </div>
        </section>

        <section className="control-panel">
          <div className="panel-heading">
            <div>
              <span className="section-index">01 / PLAN</span>
              <h2>Build your walk</h2>
            </div>

            {weather ? (
              <button
                type="button"
                className="demo-pill"
                style={{
                  cursor: 'pointer',
                  border: 'none',
                  background: 'rgba(85,224,180,0.15)',
                  color: '#55e0b4'
                }}
                onClick={() =>
                  setTemp(Math.round(weather.temperatureC))
                }
                title="Synchronize temperature slider with live Open-Meteo reading"
              >
                SYNC LIVE TEMP ({Math.round(weather.temperatureC)}°C)
              </button>
            ) : (
              <span className="demo-pill">
                REAL-WORLD WEATHER READY
              </span>
            )}
          </div>

          <div className="route-inputs">
            {/* Origin Input with Autocomplete and Geolocation */}
            <label>
              <span>FROM (START POINT)</span>

              <div className="select-wrap" ref={originInputRef}>
                <Search size={16} />

                <div className="input-autocomplete-wrap">
                  <input
                    type="text"
                    value={origin}
                    placeholder="Search address, landmark or city..."
                    onChange={(e) => {
                      setOrigin(e.target.value);
                      setOriginCoords(null);
                      setShowOriginMenu(true);
                    }}
                    onFocus={() => setShowOriginMenu(true)}
                  />

                  {origin && (
                    <button
                      type="button"
                      className="clear-btn"
                      onClick={() => {
                        setOrigin('');
                        setOriginCoords(null);
                      }}
                      title="Clear origin"
                    >
                      <X size={14} />
                    </button>
                  )}

                  <button
                    type="button"
                    className="gps-inline-btn"
                    onClick={useMyLocation}
                    title="Use current GPS location"
                  >
                    <LocateFixed size={12} />
                    GPS
                  </button>

                  {showOriginMenu && originSuggestions.length > 0 && (
                    <div className="autocomplete-dropdown">
                      {originSuggestions.map((item, idx) => (
                        <div
                          key={idx}
                          className="autocomplete-item"
                          onClick={() => {
                            setOrigin(item.name || item.displayName);
                            setOriginCoords({
                              lat: item.lat,
                              lng: item.lng
                            });
                            setShowOriginMenu(false);
                            setLocationStatus(
                              `Selected: ${item.displayName}`
                            );
                          }}
                        >
                          <b>{item.name}</b>
                          <small>{item.displayName}</small>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </label>

            <div className="route-connector">
              <i />
              <i />
            </div>

            {/* Destination Input with Autocomplete */}
            <label>
              <span>TO (DESTINATION)</span>

              <div className="select-wrap" ref={destInputRef}>
                <MapPin size={16} />

                <div className="input-autocomplete-wrap">
                  <input
                    type="text"
                    value={destination}
                    placeholder="Search destination anywhere..."
                    onChange={(e) => {
                      setDestination(e.target.value);
                      setDestCoords(null);
                      setShowDestMenu(true);
                    }}
                    onFocus={() => setShowDestMenu(true)}
                  />

                  {destination && (
                    <button
                      type="button"
                      className="clear-btn"
                      onClick={() => {
                        setDestination('');
                        setDestCoords(null);
                      }}
                      title="Clear destination"
                    >
                      <X size={14} />
                    </button>
                  )}

                  {showDestMenu && destSuggestions.length > 0 && (
                    <div className="autocomplete-dropdown">
                      {destSuggestions.map((item, idx) => (
                        <div
                          key={idx}
                          className="autocomplete-item"
                          onClick={() => {
                            setDestination(
                              item.name || item.displayName
                            );
                            setDestCoords({
                              lat: item.lat,
                              lng: item.lng
                            });
                            setShowDestMenu(false);
                          }}
                        >
                          <b>{item.name}</b>
                          <small>{item.displayName}</small>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </label>

            {/* Air Temperature Slider */}
            <label className="temp-control">
              <span>
                <Sun size={13} /> AIR TEMPERATURE
              </span>

              <div className="temp-value">
                <input
                  type="range"
                  min="20"
                  max="45"
                  value={temp}
                  onChange={(e) => setTemp(+e.target.value)}
                />

                <b>{temp}°C</b>
              </div>
            </label>

            {/* Submit / Analyze Button */}
            <button
              type="button"
              className="analyze-btn"
              disabled={loading}
              onClick={() => submit()}
            >
              <Navigation size={16} />
              {loading ? 'ANALYZING...' : 'EVALUATE ROUTES'}
            </button>
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: 10,
              marginTop: 12
            }}
          >
            <div className="location-status-pill">
              <LocateFixed size={12} color="#55e0b4" />
              <span>{locationStatus}</span>
            </div>

            {/* Preset quick test shortcuts */}
            <div
              style={{
                display: 'flex',
                gap: 6,
                alignItems: 'center'
              }}
            >
              <span
                style={{
                  font: '10px "DM Mono"',
                  color: '#7a9497'
                }}
              >
                QUICK PRESETS:
              </span>

              {PRESETS.map((p, idx) => (
                <button
                  key={idx}
                  type="button"
                  style={{
                    background: 'rgba(255,255,255,0.05)',
                    border: '1px solid #2d454a',
                    borderRadius: 4,
                    color: '#c2d6d3',
                    fontSize: 10,
                    padding: '3px 7px',
                    cursor: 'pointer',
                    fontFamily: 'Manrope, sans-serif'
                  }}
                  onClick={() => applyPreset(p)}
                >
                  {p.label.split(':')[1]?.trim() || p.label}
                </button>
              ))}
            </div>
          </div>

          {/* Profile intent selectors */}
          <div className="profile-row">
            <span>ROUTE INTENT</span>

            {profiles.map((p) => (
              <button
                key={p.id}
                className={`profile-chip ${
                  profile === p.id ? 'active' : ''
                }`}
                onClick={() => handleProfileSelect(p.id)}
              >
                <b>{p.label}</b>
                <small>{p.desc}</small>
              </button>
            ))}
          </div>
        </section>

        {error && (
          <div className="error-banner">
            <Info size={16} />
            {error}
          </div>
        )}

        <section className="results-head">
          <div>
            <span className="section-index">02 / INTELLIGENCE</span>
            <h2>Your route landscape</h2>
          </div>

          <div className="results-meta">
            <span className="live-dot" />{' '}
            {result?.routes.length || 0} ALTERNATIVES EVALUATED
          </div>
        </section>

        <section className="dashboard-grid">
          <div className="map-card">
            <div className="map-topline">
              <span>
                <span className="map-pulse" /> SPATIAL OVERVIEW
              </span>

              <span>
                {origin} → {destination}
              </span>
            </div>

            <div className="cooling-map-toolbar">
              <button
                type="button"
                className="add-cooling-button"
                onClick={() => {
                  setAddingCoolingPoint(true);
                  setPickedCoolingPoint(null);
                }}
              >
                + Add Cooling Point
              </button>

              {addingCoolingPoint && (
                <span>
                  Click on the map to select a location
                </span>
              )}
            </div>

            <MapView
              coolingPoints={coolingPoints}
              addingCoolingPoint={addingCoolingPoint}
              onMapPick={(lat, lng) => {
                setPickedCoolingPoint({ lat, lng });
                setAddingCoolingPoint(false);
              }}
              locations={locations}
              routes={result?.routes || []}
              activeRoute={active}
              onSelectRoute={(id) => setSelected(id)}
              center={
                active?.coordinates?.[0] ||
                (originCoords
                  ? [originCoords.lat, originCoords.lng]
                  : [23.2599, 77.4126])
              }
              userLocation={userLocation}
              originLabel={origin}
              destLabel={destination}
            />

            <div className="map-legend">
              <span>
                <i className="legend-route" /> SELECTED ROUTE
              </span>

              <span>
                <i
                  style={{
                    width: 12,
                    height: 3,
                    borderTop: '2px dashed #386367',
                    display: 'inline-block'
                  }}
                />{' '}
                ALTERNATIVE ROUTE
              </span>

              <span>
                <i
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: '#55e0b4',
                    display: 'inline-block'
                  }}
                />{' '}
                START
              </span>

              <span>
                <i
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: '#ff6b6b',
                    display: 'inline-block'
                  }}
                />{' '}
                DESTINATION
              </span>

              <span>
                <i className="legend-cool" /> COOLING STOP
              </span>
            </div>
          </div>

          <div className="route-list-card">
            <div className="card-title">
              <div>
                <span className="section-index">03 / COMPARE</span>
                <h3>Route options</h3>
              </div>

              <SlidersHorizontal size={17} />
            </div>

            {result?.routes.map((r, i) => (
              <button
                key={r.id}
                onClick={() => setSelected(r.id)}
                className={`route-option ${
                  active?.id === r.id ? 'chosen' : ''
                }`}
              >
                <div className="route-option-top">
                  <span className="route-num">0{i + 1}</span>

                  {r.recommended && (
                    <span className="recommended">TOP MATCH</span>
                  )}

                  <span className="route-score">
                    {r.suitability_score !== undefined
                      ? Math.round(r.suitability_score)
                      : Math.max(1, Math.round(100 - r.score))}
                    <small>/100</small>
                  </span>
                </div>

                <h4>{r.name}</h4>

                <div className="route-stats">
                  <span>
                    <Footprints size={13} />
                    {fmt(r.metrics.distance_m)}
                  </span>

                  <span>
                    <Clock3 size={13} />
                    {Math.round(r.metrics.duration_min)} min
                  </span>
                </div>

                <div className="score-track">
                  <i
                    style={{
                      width: `${
                        Math.min(
                          100,
                          Math.max(
                            5,
                            r.suitability_score !== undefined
                              ? r.suitability_score
                              : 100 - r.score
                          )
                        )
                      }%`
                    }}
                  />
                </div>

                <small className="score-caption">
                  {r.recommended
                    ? 'Best overall match for ' + profile + ' intent'
                    : 'Pedestrian suitability rating'}
                </small>
              </button>
            ))}

            {!result && (
              <div className="empty-state">
                Choose your endpoints to analyze route alternatives.
              </div>
            )}

            <div className="route-note">
              <Info size={14} /> Higher suitability score indicates a more
              suitable walk for your chosen intent profile.
            </div>
          </div>
        </section>

        <section className="detail-grid">
          <div className="detail-card">
            <div className="card-title">
              <div>
                <span className="section-index">04 / WHY THIS ROUTE</span>
                <h3>{active?.name || 'Route breakdown'}</h3>
              </div>

              <ShieldCheck size={19} />
            </div>

            {active ? (
              <>
                <div className="metric-grid">
                  <Metric
                    icon={<Flame />}
                    label="HEAT BURDEN"
                    value={`${Math.round(active.metrics.heat || 0)}/100`}
                    sub={
                      active.metrics.shade_coverage_pct !== undefined
                        ? `${Math.round(
                            active.metrics.shade_coverage_pct * 100
                          )}% tree canopy`
                        : 'ESTIMATED (Shade proxy)'
                    }
                  />

                  <Metric
                    icon={<Sun />}
                    label="LIGHTING / VISIBILITY"
                    value={`${Math.round(
                      (active.metrics.lighting ||
                        active.metrics.visibility ||
                        0.6) * 100
                    )}/100`}
                    sub={
                      active.data_trust?.lighting || 'ESTIMATED (OSM)'
                    }
                  />

                  <Metric
                    icon={<Activity />}
                    label="PEDESTRIAN INFRA"
                    value={
                      active.metrics.sidewalk_pct !== undefined
                        ? `${Math.round(
                            active.metrics.sidewalk_pct * 100
                          )}% sidewalks`
                        : `${Math.round(
                            (active.metrics.crowd || 0.5) * 100
                          )}/100`
                    }
                    sub="OSM Footways & Crossings"
                  />

                  <Metric
                    icon={<TreePine />}
                    label="COOLING & GREENERY"
                    value={
                      active.cooling_stops?.length > 0
                        ? `${active.cooling_stops.length} rest points`
                        : active.metrics.cooling_access < 40
                          ? 'Accessible'
                          : 'Standard'
                    }
                    sub={
                      active.metrics.greenery_pct !== undefined
                        ? `${Math.round(
                            active.metrics.greenery_pct * 100
                          )}% park proximity`
                        : 'Parks & Cooling POIs'
                    }
                  />
                </div>

                <div className="reasons">
                  {active.reasons.map((r, i) => (
                    <p key={i}>
                      <span>0{i + 1}</span>
                      {r}
                    </p>
                  ))}
                </div>
              </>
            ) : (
              <p className="muted">
                Route analysis will appear here.
              </p>
            )}
          </div>

          <div className="detail-card score-card">
            <div className="card-title">
              <div>
                <span className="section-index">COST MODEL</span>
                <h3>Factor contribution</h3>
              </div>

              <Zap size={18} />
            </div>

            {active &&
              Object.entries(active.breakdown).map(([key, val]) => (
                <div className="factor" key={key}>
                  <div>
                    <span>{key.replace(/_/g, ' ')}</span>
                    <b>{val.toFixed(1)}</b>
                  </div>

                  <div className="factor-track">
                    <i
                      style={{
                        width: `${Math.min(
                          100,
                          Math.max(5, val * 2.5)
                        )}%`
                      }}
                    />
                  </div>
                </div>
              ))}

            <div className="model-foot">
              WEIGHTED MULTI-FACTOR RANKING{' '}
              <span>{profile.toUpperCase()} PROFILE</span>
            </div>
          </div>
        </section>

        <footer>
          <div className="footer-brand">
            safe<span>route</span>{' '}
            <small>URBAN WALK INTELLIGENCE · MARGNOVA</small>
          </div>

          <p>
            MargNova provides estimated route suitability information and
            does not guarantee personal safety. Environmental indicators
            and cooling points are illustrative and must be verified
            before real-world use.
          </p>

          <span className="footer-version">
            BUILT FOR PUBLIC GOOD · 2026
          </span>
        </footer>
      </main>

      {pickedCoolingPoint && (
        <CoolingPointForm
          latitude={pickedCoolingPoint.lat}
          longitude={pickedCoolingPoint.lng}
          onSave={handleCoolingPointSave}
          onCancel={() => setPickedCoolingPoint(null)}
        />
      )}
    </div>
  );
}

function Metric({
  icon,
  label,
  value,
  sub
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  sub: string;
}) {
  return (
    <div className="metric">
      <div className="metric-icon">{icon}</div>
      <span>{label}</span>
      <b>{value}</b>
      <small>{sub}</small>
    </div>
  );
}