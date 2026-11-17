import Foundation
import SQLite3
import Shared

/// The cleared spots in a SQLite file, same table layout as Android's trails.db. The iOS PointStore.
final class SQLitePointStore: NSObject, PointStore {
    private var db: OpaquePointer?

    init(path: URL) {
        super.init()
        try? FileManager.default.createDirectory(at: path.deletingLastPathComponent(), withIntermediateDirectories: true)
        guard sqlite3_open(path.path, &db) == SQLITE_OK else { fatalError("cannot open \(path.path)") }
        exec("""
            CREATE TABLE IF NOT EXISTS points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                lat REAL NOT NULL, lon REAL NOT NULL, time INTEGER NOT NULL,
                radius REAL NOT NULL DEFAULT 50.0, session_id INTEGER)
            """)
    }

    deinit { sqlite3_close(db) }

    func insert(points: [TrailPoint]) {
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
        var out: [TrailPoint] = []
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "SELECT lat, lon, time, radius, session_id FROM points ORDER BY id", -1, &stmt, nil)
        while sqlite3_step(stmt) == SQLITE_ROW {
            let session: KotlinLong? = sqlite3_column_type(stmt, 4) == SQLITE_NULL ? nil : KotlinLong(value: sqlite3_column_int64(stmt, 4))
            out.append(TrailPoint(lat: sqlite3_column_double(stmt, 0), lon: sqlite3_column_double(stmt, 1),
                                  time: sqlite3_column_int64(stmt, 2), radius: sqlite3_column_double(stmt, 3), sessionId: session))
        }
        sqlite3_finalize(stmt)
        return out
    }

    func countPoints() -> Int64 {
        var stmt: OpaquePointer?
        sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM points", -1, &stmt, nil)
        defer { sqlite3_finalize(stmt) }
        return sqlite3_step(stmt) == SQLITE_ROW ? sqlite3_column_int64(stmt, 0) : 0
    }

    private func exec(_ sql: String) { sqlite3_exec(db, sql, nil, nil, nil) }
}
