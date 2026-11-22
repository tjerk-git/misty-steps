import Foundation
import SQLite3
import Shared

/// Spots and walks in a SQLite file, same table layout as Android's trails.db. The iOS PointStore and SessionStore.
private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

final class SQLiteTrailStore: NSObject, PointStore, SessionStore, PinStorage {
    private var db: OpaquePointer?
    /// Every call takes this: the recorder, the stats and the postcards use one connection from several threads.
    private let lock = NSRecursiveLock()

    init(path: URL) {
        super.init()
        try? FileManager.default.createDirectory(at: path.deletingLastPathComponent(), withIntermediateDirectories: true)
        guard sqlite3_open_v2(path.path, &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil) == SQLITE_OK else { fatalError("cannot open \(path.path)") }
        exec("""
            CREATE TABLE IF NOT EXISTS points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                lat REAL NOT NULL, lon REAL NOT NULL, time INTEGER NOT NULL,
                radius REAL NOT NULL DEFAULT 50.0, session_id INTEGER)
            """)
        exec("""
            CREATE TABLE IF NOT EXISTS sessions (
                id INTEGER PRIMARY KEY, start_time INTEGER NOT NULL, end_time INTEGER,
                distance_m REAL NOT NULL DEFAULT 0, points INTEGER NOT NULL DEFAULT 0,
                battery_start INTEGER, battery_end INTEGER, new_area_m2 REAL)
            """)
        exec("CREATE INDEX IF NOT EXISTS points_session ON points(session_id)")
        exec("""
            CREATE TABLE IF NOT EXISTS pins (
                id INTEGER PRIMARY KEY AUTOINCREMENT, lat REAL NOT NULL, lon REAL NOT NULL, time INTEGER NOT NULL,
                label TEXT NOT NULL, note TEXT, session_id INTEGER)
            """)
        exec("CREATE INDEX IF NOT EXISTS pins_session ON pins(session_id)")
    }

    deinit { sqlite3_close(db) }

    func insert(points: [TrailPoint]) {
        lock.lock(); defer { lock.unlock() }
        exec("BEGIN")
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "INSERT INTO points (lat, lon, time, radius, session_id) VALUES (?, ?, ?, ?, ?)", -1, &stmt, nil)
        for p in points {
            sqlite3_bind_double(stmt, 1, p.lat)
            sqlite3_bind_double(stmt, 2, p.lon)
            sqlite3_bind_int64(stmt, 3, p.time)
            sqlite3_bind_double(stmt, 4, p.radius)
            if let s = p.sessionId { sqlite3_bind_int64(stmt, 5, s.int64Value) } else { sqlite3_bind_null(stmt, 5) }
            sqlite3_step(stmt)
            sqlite3_reset(stmt)
        }
        sqlite3_finalize(stmt)
        exec("COMMIT")
    }

    func loadAll() -> [TrailPoint] {
        lock.lock(); defer { lock.unlock() }
        var out: [TrailPoint] = []
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "SELECT lat, lon, time, radius, session_id FROM points ORDER BY id", -1, &stmt, nil)
        while sqlite3_step(stmt) == SQLITE_ROW {
            let session: KotlinLong? = sqlite3_column_type(stmt, 4) == SQLITE_NULL ? nil : KotlinLong(value: sqlite3_column_int64(stmt, 4))
            out.append(TrailPoint(lat: sqlite3_column_double(stmt, 0), lon: sqlite3_column_double(stmt, 1),
                                  time: sqlite3_column_int64(stmt, 2), radius: sqlite3_column_double(stmt, 3), sessionId: session, eraseStroke: nil))
        }
        sqlite3_finalize(stmt)
        return out
    }

    func startSession(s: Session) {
        lock.lock(); defer { lock.unlock() }
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "INSERT INTO sessions (id, start_time, battery_start) VALUES (?, ?, ?)", -1, &stmt, nil)
        sqlite3_bind_int64(stmt, 1, s.id)
        sqlite3_bind_int64(stmt, 2, s.startTime)
        if let b = s.batteryStart { sqlite3_bind_int(stmt, 3, b.int32Value) } else { sqlite3_bind_null(stmt, 3) }
        sqlite3_step(stmt)
        sqlite3_finalize(stmt)
    }

    func endSession(s: Session) {
        lock.lock(); defer { lock.unlock() }
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "UPDATE sessions SET end_time = ?, distance_m = ?, points = ?, battery_end = ?, new_area_m2 = ? WHERE id = ?", -1, &stmt, nil)
        if let e = s.endTime { sqlite3_bind_int64(stmt, 1, e.int64Value) } else { sqlite3_bind_null(stmt, 1) }
        sqlite3_bind_double(stmt, 2, s.distanceM)
        sqlite3_bind_int(stmt, 3, s.points)
        if let b = s.batteryEnd { sqlite3_bind_int(stmt, 4, b.int32Value) } else { sqlite3_bind_null(stmt, 4) }
        if let a = s.newAreaM2 { sqlite3_bind_double(stmt, 5, a.doubleValue) } else { sqlite3_bind_null(stmt, 5) }
        sqlite3_bind_int64(stmt, 6, s.id)
        sqlite3_step(stmt)
        sqlite3_finalize(stmt)
    }

    func loadSessions(limit: KotlinInt?) -> [Session] {
        lock.lock(); defer { lock.unlock() }
        var out: [Session] = []
        var stmt: OpaquePointer?
        let sql = "SELECT id, start_time, end_time, distance_m, points, battery_start, battery_end, new_area_m2 FROM sessions ORDER BY start_time DESC"
            + (limit.map { " LIMIT \($0.intValue)" } ?? "")
        sqlite3_prepare_v2(db, sql, -1, &stmt, nil)
        while sqlite3_step(stmt) == SQLITE_ROW {
            func isNull(_ i: Int32) -> Bool { sqlite3_column_type(stmt, i) == SQLITE_NULL }
            out.append(Session(
                id: sqlite3_column_int64(stmt, 0), startTime: sqlite3_column_int64(stmt, 1),
                endTime: isNull(2) ? nil : KotlinLong(value: sqlite3_column_int64(stmt, 2)),
                distanceM: sqlite3_column_double(stmt, 3), points: sqlite3_column_int(stmt, 4),
                batteryStart: isNull(5) ? nil : KotlinInt(value: sqlite3_column_int(stmt, 5)),
                batteryEnd: isNull(6) ? nil : KotlinInt(value: sqlite3_column_int(stmt, 6)),
                newAreaM2: isNull(7) ? nil : KotlinDouble(value: sqlite3_column_double(stmt, 7))))
        }
        sqlite3_finalize(stmt)
        return out
    }

    // MARK: pins (same table as Android's)

    func insertPin(p: Pin) -> Int64 {
        lock.lock(); defer { lock.unlock() }
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "INSERT INTO pins (lat, lon, time, label, note, session_id) VALUES (?, ?, ?, ?, ?, ?)", -1, &stmt, nil)
        sqlite3_bind_double(stmt, 1, p.lat)
        sqlite3_bind_double(stmt, 2, p.lon)
        sqlite3_bind_int64(stmt, 3, p.time)
        sqlite3_bind_text(stmt, 4, p.labelKey, -1, SQLITE_TRANSIENT)
        if let n = p.note { sqlite3_bind_text(stmt, 5, n, -1, SQLITE_TRANSIENT) } else { sqlite3_bind_null(stmt, 5) }
        if let s = p.sessionId { sqlite3_bind_int64(stmt, 6, s.int64Value) } else { sqlite3_bind_null(stmt, 6) }
        sqlite3_step(stmt)
        sqlite3_finalize(stmt)
        return sqlite3_last_insert_rowid(db)
    }

    func updatePin(id: Int64, labelKey: String, note: String?) {
        lock.lock(); defer { lock.unlock() }
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "UPDATE pins SET label = ?, note = ? WHERE id = ?", -1, &stmt, nil)
        sqlite3_bind_text(stmt, 1, labelKey, -1, SQLITE_TRANSIENT)
        if let n = note { sqlite3_bind_text(stmt, 2, n, -1, SQLITE_TRANSIENT) } else { sqlite3_bind_null(stmt, 2) }
        sqlite3_bind_int64(stmt, 3, id)
        sqlite3_step(stmt)
        sqlite3_finalize(stmt)
    }

    func deletePin(id: Int64) {
        lock.lock(); defer { lock.unlock() }
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "DELETE FROM pins WHERE id = ?", -1, &stmt, nil)
        sqlite3_bind_int64(stmt, 1, id)
        sqlite3_step(stmt)
        sqlite3_finalize(stmt)
    }

    func loadPins() -> [Pin] {
        lock.lock(); defer { lock.unlock() }
        var out: [Pin] = []
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "SELECT id, lat, lon, time, label, note, session_id FROM pins ORDER BY time DESC, id DESC", -1, &stmt, nil)
        while sqlite3_step(stmt) == SQLITE_ROW {
            let note: String? = sqlite3_column_type(stmt, 5) == SQLITE_NULL ? nil : String(cString: sqlite3_column_text(stmt, 5))
            let session: KotlinLong? = sqlite3_column_type(stmt, 6) == SQLITE_NULL ? nil : KotlinLong(value: sqlite3_column_int64(stmt, 6))
            out.append(Pin(id: sqlite3_column_int64(stmt, 0), lat: sqlite3_column_double(stmt, 1), lon: sqlite3_column_double(stmt, 2),
                           time: sqlite3_column_int64(stmt, 3), labelKey: String(cString: sqlite3_column_text(stmt, 4)),
                           note: note, sessionId: session))
        }
        sqlite3_finalize(stmt)
        return out
    }

    /// One walk, or nil.
    func loadSession(id: Int64) -> Session? {
        loadSessions(limit: nil).first { $0.id == id }
    }

    /// The spots of one walk, in order.
    func loadSessionPoints(id: Int64) -> [TrailPoint] {
        lock.lock(); defer { lock.unlock() }
        var out: [TrailPoint] = []
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "SELECT lat, lon, time, radius, session_id FROM points WHERE session_id = ? ORDER BY id", -1, &stmt, nil)
        sqlite3_bind_int64(stmt, 1, id)
        while sqlite3_step(stmt) == SQLITE_ROW {
            let session: KotlinLong? = sqlite3_column_type(stmt, 4) == SQLITE_NULL ? nil : KotlinLong(value: sqlite3_column_int64(stmt, 4))
            out.append(TrailPoint(lat: sqlite3_column_double(stmt, 0), lon: sqlite3_column_double(stmt, 1),
                                  time: sqlite3_column_int64(stmt, 2), radius: sqlite3_column_double(stmt, 3), sessionId: session, eraseStroke: nil))
        }
        sqlite3_finalize(stmt)
        return out
    }

    /// The spots of other walks (and of before walks were logged) inside a box, for working out what a walk cleared that was new.
    func loadOtherPointsIn(id: Int64, minLat: Double, minLon: Double, maxLat: Double, maxLon: Double) -> [TrailPoint] {
        lock.lock(); defer { lock.unlock() }
        var out: [TrailPoint] = []
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, """
            SELECT lat, lon, time, radius, session_id FROM points
            WHERE (session_id IS NULL OR session_id != ?) AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?
            """, -1, &stmt, nil)
        sqlite3_bind_int64(stmt, 1, id)
        sqlite3_bind_double(stmt, 2, minLat); sqlite3_bind_double(stmt, 3, maxLat)
        sqlite3_bind_double(stmt, 4, minLon); sqlite3_bind_double(stmt, 5, maxLon)
        while sqlite3_step(stmt) == SQLITE_ROW {
            let session: KotlinLong? = sqlite3_column_type(stmt, 4) == SQLITE_NULL ? nil : KotlinLong(value: sqlite3_column_int64(stmt, 4))
            out.append(TrailPoint(lat: sqlite3_column_double(stmt, 0), lon: sqlite3_column_double(stmt, 1),
                                  time: sqlite3_column_int64(stmt, 2), radius: sqlite3_column_double(stmt, 3), sessionId: session, eraseStroke: nil))
        }
        sqlite3_finalize(stmt)
        return out
    }

    /// Adds a backup's walks and spots. Only adds: spots already here (same place and time) and walks already here (same id) are
    /// skipped, nothing is changed or deleted. Returns how many spots and walks were added.
    func importData(points: [TrailPoint], sessions: [Session]) -> BackupCounts {
        lock.lock(); defer { lock.unlock() }
        var have = Set<String>()
        var read: OpaquePointer?
        sqlite3_prepare_v2(db, "SELECT lat, lon, time FROM points", -1, &read, nil)
        while sqlite3_step(read) == SQLITE_ROW {
            have.insert("\(sqlite3_column_double(read, 0)),\(sqlite3_column_double(read, 1)),\(sqlite3_column_int64(read, 2))")
        }
        sqlite3_finalize(read)
        var addedSessions: Int32 = 0, addedPoints: Int32 = 0
        exec("BEGIN")
        var s: OpaquePointer?
        sqlite3_prepare_v2(db, """
            INSERT OR IGNORE INTO sessions (id, start_time, end_time, distance_m, points, battery_start, battery_end, new_area_m2)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, -1, &s, nil)
        for x in sessions {
            sqlite3_bind_int64(s, 1, x.id); sqlite3_bind_int64(s, 2, x.startTime)
            if let e = x.endTime { sqlite3_bind_int64(s, 3, e.int64Value) } else { sqlite3_bind_null(s, 3) }
            sqlite3_bind_double(s, 4, x.distanceM); sqlite3_bind_int(s, 5, x.points)
            if let b = x.batteryStart { sqlite3_bind_int(s, 6, b.int32Value) } else { sqlite3_bind_null(s, 6) }
            if let b = x.batteryEnd { sqlite3_bind_int(s, 7, b.int32Value) } else { sqlite3_bind_null(s, 7) }
            if let a = x.newAreaM2 { sqlite3_bind_double(s, 8, a.doubleValue) } else { sqlite3_bind_null(s, 8) }
            sqlite3_step(s)
            if sqlite3_changes(db) > 0 { addedSessions += 1 }
            sqlite3_reset(s)
        }
        sqlite3_finalize(s)
        var p: OpaquePointer?
        sqlite3_prepare_v2(db, "INSERT INTO points (lat, lon, time, radius, session_id) VALUES (?, ?, ?, ?, ?)", -1, &p, nil)
        for x in points {
            guard have.insert("\(x.lat),\(x.lon),\(x.time)").inserted else { continue }
            sqlite3_bind_double(p, 1, x.lat); sqlite3_bind_double(p, 2, x.lon)
            sqlite3_bind_int64(p, 3, x.time); sqlite3_bind_double(p, 4, x.radius)
            if let id = x.sessionId { sqlite3_bind_int64(p, 5, id.int64Value) } else { sqlite3_bind_null(p, 5) }
            sqlite3_step(p)
            sqlite3_reset(p)
            addedPoints += 1
        }
        sqlite3_finalize(p)
        exec("COMMIT")
        return BackupCounts(points: addedPoints, walks: addedSessions, pins: 0)
    }

    /// Adds a backup's pins, skipping ones already here (same time and place). Returns how many were added.
    func importPins(_ incoming: [Pin]) -> Int32 {
        lock.lock(); defer { lock.unlock() }
        let fresh = PinRules.shared.doNewPins(existing: loadPins(), incoming: incoming) as? [Pin] ?? []
        for pin in fresh { _ = insertPin(p: pin) }
        return Int32(fresh.count)
    }

    func isEmpty() -> Bool { countPoints() == 0 }

    func countPoints() -> Int64 {
        lock.lock(); defer { lock.unlock() }
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM points", -1, &stmt, nil)
        defer { sqlite3_finalize(stmt) }
        return sqlite3_step(stmt) == SQLITE_ROW ? sqlite3_column_int64(stmt, 0) : 0
    }

    private func exec(_ sql: String) { sqlite3_exec(db, sql, nil, nil, nil) }
}
