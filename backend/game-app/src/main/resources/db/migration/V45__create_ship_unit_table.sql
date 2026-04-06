-- V45: ShipUnit 엔티티 테이블 (gin7 함종 유닛 — 300척 단위 부대)
-- ShipClass: BATTLESHIP/CRUISER/DESTROYER/CARRIER/TRANSPORT/HOSPITAL/FORTRESS
-- ShipSubtype: 각 함종별 세부 타입 (예: BATTLESHIP_I, BATTLESHIP_II 등)
-- CrewProficiency: GREEN/NORMAL/VETERAN/ELITE

CREATE TABLE ship_unit (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT NOT NULL,
    fleet_id            BIGINT NOT NULL REFERENCES fleet(id) ON DELETE CASCADE,
    slot_index          SMALLINT NOT NULL DEFAULT 0,  -- 함대 내 부대 번호 (0~7)

    -- 함종 정보
    ship_class          VARCHAR(32) NOT NULL DEFAULT 'BATTLESHIP',
    ship_subtype        VARCHAR(32) NOT NULL DEFAULT 'BATTLESHIP_I',

    -- 함선 수
    ship_count          INT NOT NULL DEFAULT 0,
    max_ship_count      INT NOT NULL DEFAULT 300,

    -- 기본 전투 수치 (ShipStatRegistry에서 로드하여 갱신)
    armor               INT NOT NULL DEFAULT 0,
    shield              INT NOT NULL DEFAULT 0,
    weapon_power        INT NOT NULL DEFAULT 0,
    speed               INT NOT NULL DEFAULT 0,
    crew_capacity       INT NOT NULL DEFAULT 0,
    supply_capacity     INT NOT NULL DEFAULT 0,

    -- 상태
    morale              SMALLINT NOT NULL DEFAULT 50,
    training            SMALLINT NOT NULL DEFAULT 50,
    missile_stock       INT NOT NULL DEFAULT 100,
    stance              VARCHAR(16) NOT NULL DEFAULT 'CRUISE',
    crew_proficiency    VARCHAR(16) NOT NULL DEFAULT 'GREEN',

    -- 기함 정보
    is_flagship         BOOLEAN NOT NULL DEFAULT FALSE,
    flagship_code       VARCHAR(64) NOT NULL DEFAULT '',

    -- 지상부대 탑재 (수송함/착륙함)
    ground_unit_type    VARCHAR(32) NOT NULL DEFAULT '',
    ground_unit_count   INT NOT NULL DEFAULT 0,

    -- 유연 필드
    meta                JSONB NOT NULL DEFAULT '{}',

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 인덱스
CREATE INDEX idx_ship_unit_fleet_id   ON ship_unit(fleet_id);
CREATE INDEX idx_ship_unit_session_id ON ship_unit(session_id);
CREATE INDEX idx_ship_unit_flagship   ON ship_unit(session_id, is_flagship) WHERE is_flagship = TRUE;
