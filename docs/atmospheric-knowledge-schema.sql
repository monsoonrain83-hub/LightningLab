CREATE INDEX idx_altitude_phenomenon ON altitude_profiles(phenomenon_code);

CREATE INDEX idx_conditions_phenomenon ON formation_conditions(phenomenon_code);

CREATE INDEX idx_images_phenomenon ON reference_images(phenomenon_code);

CREATE INDEX idx_impacts_phenomenon ON impacts(phenomenon_code);

CREATE INDEX idx_species_genus ON cloud_species(genus_codes);

CREATE TABLE altitude_profiles(
 id INTEGER PRIMARY KEY, phenomenon_code TEXT NOT NULL,
 layer TEXT NOT NULL, range_note_fa TEXT NOT NULL,
 numeric_min_m REAL, numeric_max_m REAL, qualification_fa TEXT NOT NULL,
 source_code TEXT
);

CREATE TABLE cloud_features(
 id INTEGER PRIMARY KEY, code TEXT UNIQUE NOT NULL, name_en TEXT NOT NULL,
 name_fa TEXT NOT NULL, description_fa TEXT NOT NULL, relation_note_fa TEXT,
 source_code TEXT
);

CREATE TABLE cloud_accessory_clouds(
 id INTEGER PRIMARY KEY, code TEXT UNIQUE NOT NULL, name_en TEXT NOT NULL,
 name_fa TEXT NOT NULL, description_fa TEXT NOT NULL, relation_note_fa TEXT,
 source_code TEXT
);

CREATE TABLE cloud_genera(
 phenomenon_id INTEGER PRIMARY KEY REFERENCES phenomena(id) ON DELETE CASCADE,
 abbreviation TEXT UNIQUE NOT NULL, family TEXT NOT NULL,
 typical_base_fa TEXT, vertical_extent_fa TEXT, formation_fa TEXT,
 weather_fa TEXT
);

CREATE TABLE cloud_species(
 id INTEGER PRIMARY KEY, code TEXT UNIQUE NOT NULL, name_en TEXT NOT NULL,
 name_fa TEXT NOT NULL, genus_codes TEXT NOT NULL, description_fa TEXT NOT NULL,
 source_code TEXT
);

CREATE TABLE cloud_varieties(
 id INTEGER PRIMARY KEY, code TEXT UNIQUE NOT NULL, name_en TEXT NOT NULL,
 name_fa TEXT NOT NULL, description_fa TEXT NOT NULL, source_code TEXT,
 genus_codes TEXT NOT NULL DEFAULT ''
);

CREATE TABLE formation_conditions(
 id INTEGER PRIMARY KEY, phenomenon_code TEXT NOT NULL,
 condition_fa TEXT NOT NULL, mechanism_fa TEXT NOT NULL,
 confidence_note_fa TEXT, source_code TEXT
);

CREATE TABLE impacts(
 id INTEGER PRIMARY KEY, phenomenon_code TEXT NOT NULL,
 impact_type TEXT NOT NULL, description_fa TEXT NOT NULL,
 likelihood_note_fa TEXT, source_code TEXT
);

CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL);

CREATE TABLE phenomena(
 id INTEGER PRIMARY KEY, code TEXT UNIQUE NOT NULL, name_en TEXT NOT NULL,
 name_fa TEXT NOT NULL, category TEXT NOT NULL, summary_fa TEXT NOT NULL,
 visual_cues_fa TEXT, caveats_fa TEXT, source_code TEXT
);

CREATE TABLE reference_images(
 id INTEGER PRIMARY KEY, phenomenon_code TEXT NOT NULL,
 asset_path TEXT, thumbnail_path TEXT, caption_fa TEXT,
 license TEXT, attribution TEXT, verified INTEGER NOT NULL DEFAULT 0,
 status TEXT NOT NULL DEFAULT 'needs_curated_image'
);

CREATE TABLE sources(
 id INTEGER PRIMARY KEY, code TEXT UNIQUE NOT NULL, title TEXT NOT NULL,
 publisher TEXT, url TEXT, note TEXT
);
