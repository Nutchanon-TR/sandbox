CREATE SCHEMA IF NOT EXISTS jobjab;

CREATE TABLE IF NOT EXISTS jobjab.user_job_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    headline VARCHAR(160),
    desired_titles JSONB NOT NULL DEFAULT '[]'::jsonb,
    skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    experiences JSONB NOT NULL DEFAULT '[]'::jsonb,
    preferred_locations JSONB NOT NULL DEFAULT '[]'::jsonb,
    employment_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    salary_min INTEGER,
    salary_max INTEGER,
    home_location_label TEXT,
    home_latitude NUMERIC(10, 7),
    home_longitude NUMERIC(10, 7),
    max_commute_minutes INTEGER,
    travel_mode VARCHAR(32) NOT NULL DEFAULT 'DRIVE',
    weekly_digest_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS jobjab.job_sources (
    id BIGSERIAL PRIMARY KEY,
    source_key VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    fetch_mode VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    requires_partner_approval BOOLEAN NOT NULL DEFAULT false,
    robots_check_required BOOLEAN NOT NULL DEFAULT false,
    rate_limit_per_minute INTEGER NOT NULL DEFAULT 30,
    adapter_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS jobjab.search_runs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    profile_id BIGINT REFERENCES jobjab.user_job_profiles(id) ON DELETE SET NULL,
    run_type VARCHAR(24) NOT NULL DEFAULT 'ON_DEMAND',
    status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
    query TEXT,
    location_text TEXT,
    requested_limit INTEGER NOT NULL DEFAULT 10,
    source_keys JSONB NOT NULL DEFAULT '[]'::jsonb,
    criteria JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS jobjab.search_run_events (
    id BIGSERIAL PRIMARY KEY,
    search_run_id BIGINT NOT NULL REFERENCES jobjab.search_runs(id) ON DELETE CASCADE,
    level VARCHAR(16) NOT NULL DEFAULT 'INFO',
    source_key VARCHAR(64),
    message TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS jobjab.raw_job_snapshots (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES jobjab.job_sources(id) ON DELETE CASCADE,
    source_job_key TEXT NOT NULL,
    url TEXT,
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    raw_text TEXT,
    content_hash VARCHAR(128) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source_id, source_job_key, content_hash)
);

CREATE TABLE IF NOT EXISTS jobjab.jobs (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES jobjab.job_sources(id) ON DELETE CASCADE,
    source_job_key TEXT NOT NULL,
    latest_snapshot_id BIGINT REFERENCES jobjab.raw_job_snapshots(id) ON DELETE SET NULL,
    canonical_url TEXT,
    title VARCHAR(240) NOT NULL,
    company VARCHAR(200) NOT NULL DEFAULT 'Unknown company',
    location_text TEXT,
    location_latitude NUMERIC(10, 7),
    location_longitude NUMERIC(10, 7),
    salary_min INTEGER,
    salary_max INTEGER,
    currency VARCHAR(12) NOT NULL DEFAULT 'THB',
    employment_type VARCHAR(64),
    workplace_type VARCHAR(64),
    skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    description TEXT NOT NULL DEFAULT '',
    apply_url TEXT,
    posted_at TIMESTAMPTZ,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at TIMESTAMPTZ,
    UNIQUE (source_id, source_job_key)
);

CREATE TABLE IF NOT EXISTS jobjab.job_matches (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobjab.jobs(id) ON DELETE CASCADE,
    profile_id BIGINT REFERENCES jobjab.user_job_profiles(id) ON DELETE SET NULL,
    match_score INTEGER NOT NULL DEFAULT 0,
    matched_skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    red_flags JSONB NOT NULL DEFAULT '[]'::jsonb,
    ai_summary TEXT NOT NULL DEFAULT '',
    analyzed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, job_id)
);

CREATE TABLE IF NOT EXISTS jobjab.job_tracking (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobjab.jobs(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'INTERESTED',
    notes TEXT,
    applied_at TIMESTAMPTZ,
    interview_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, job_id)
);

CREATE TABLE IF NOT EXISTS jobjab.route_cache (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobjab.jobs(id) ON DELETE CASCADE,
    cache_key VARCHAR(128) NOT NULL UNIQUE,
    travel_mode VARCHAR(32) NOT NULL DEFAULT 'DRIVE',
    origin_label TEXT,
    destination_label TEXT,
    distance_meters INTEGER,
    duration_seconds INTEGER,
    provider VARCHAR(32) NOT NULL DEFAULT 'ESTIMATE',
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS jobjab.weekly_digests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    profile_id BIGINT REFERENCES jobjab.user_job_profiles(id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    week_start DATE NOT NULL,
    week_end DATE NOT NULL,
    summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ,
    UNIQUE (user_id, week_start)
);

CREATE TABLE IF NOT EXISTS jobjab.weekly_digest_items (
    id BIGSERIAL PRIMARY KEY,
    weekly_digest_id BIGINT NOT NULL REFERENCES jobjab.weekly_digests(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobjab.jobs(id) ON DELETE CASCADE,
    match_id BIGINT REFERENCES jobjab.job_matches(id) ON DELETE SET NULL,
    rank INTEGER NOT NULL,
    reason TEXT,
    UNIQUE (weekly_digest_id, job_id)
);

CREATE INDEX IF NOT EXISTS idx_jobjab_profiles_user ON jobjab.user_job_profiles (user_id);
CREATE INDEX IF NOT EXISTS idx_jobjab_search_runs_user_created ON jobjab.search_runs (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_jobjab_search_events_run_created ON jobjab.search_run_events (search_run_id, created_at ASC, id ASC);
CREATE INDEX IF NOT EXISTS idx_jobjab_raw_source_key ON jobjab.raw_job_snapshots (source_id, source_job_key);
CREATE INDEX IF NOT EXISTS idx_jobjab_jobs_source_key ON jobjab.jobs (source_id, source_job_key);
CREATE INDEX IF NOT EXISTS idx_jobjab_jobs_last_seen ON jobjab.jobs (last_seen_at DESC) WHERE archived_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_jobjab_jobs_skills_gin ON jobjab.jobs USING GIN (skills);
CREATE INDEX IF NOT EXISTS idx_jobjab_matches_user_score ON jobjab.job_matches (user_id, match_score DESC, analyzed_at DESC);
CREATE INDEX IF NOT EXISTS idx_jobjab_tracking_user_status ON jobjab.job_tracking (user_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_jobjab_route_user_job ON jobjab.route_cache (user_id, job_id, travel_mode);
CREATE INDEX IF NOT EXISTS idx_jobjab_digests_user_week ON jobjab.weekly_digests (user_id, week_start DESC);

ALTER TABLE jobjab.user_job_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobjab.job_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobjab.search_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobjab.search_run_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobjab.raw_job_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobjab.jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobjab.job_matches ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobjab.job_tracking ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobjab.route_cache ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobjab.weekly_digests ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobjab.weekly_digest_items ENABLE ROW LEVEL SECURITY;

INSERT INTO jobjab.job_sources (source_key, display_name, fetch_mode, enabled, requires_partner_approval, robots_check_required, rate_limit_per_minute, adapter_config)
VALUES
    ('user_url', 'User pasted URL', 'USER_URL', true, false, false, 120, '{}'::jsonb),
    ('manual_jd', 'Manual JD paste', 'MANUAL', true, false, false, 120, '{}'::jsonb),
    ('greenhouse', 'Greenhouse', 'OFFICIAL_API', true, false, false, 60, '{"boards":[],"example":{"boards":["example-company-token"]}}'::jsonb),
    ('lever', 'Lever', 'OFFICIAL_API', true, false, false, 60, '{"companies":[],"example":{"companies":["example-company-site"]}}'::jsonb),
    ('ashby', 'Ashby', 'OFFICIAL_API', true, false, false, 60, '{"boards":[],"example":{"boards":["example-job-board-name"]}}'::jsonb),
    ('structured_data', 'Structured JobPosting data', 'STRUCTURED_DATA', true, false, true, 30, '{}'::jsonb),
    ('sitemap', 'Sitemap job URLs', 'SITEMAP', true, false, true, 20, '{"sitemapUrls":[],"example":{"sitemapUrls":["https://example.com/sitemap.xml"]}}'::jsonb),
    ('jobsdb', 'JobsDB/SEEK', 'PARTNER_API', false, true, true, 10, '{}'::jsonb),
    ('jobthai', 'JobThai', 'HTML', false, false, true, 10, '{}'::jsonb),
    ('linkedin', 'LinkedIn', 'PARTNER_API', false, true, true, 10, '{}'::jsonb)
ON CONFLICT (source_key) DO NOTHING;
