#!/usr/bin/env python3
# Duplicate-seat guarantee test end-to-end.
#   1) create a dedicated exam (small audience),
#   2) create a dedicated room,
#   3) generate the chart,
#   4) fetch /api/allocations and /api/seats and assert: no two
#      SeatAllocation rows share the same (exam_id, seat_id), and no two
#      students are assigned to the same seat on the same exam.
import json, os, sys, time, subprocess, urllib.request, urllib.parse, urllib.error

APP = os.environ.get("APP_URL", "http://localhost:8080")
SESSION = {}
LOGIN_ENDPOINT = f"{APP}/api/auth/login"
CSRF_ENDPOINT = f"{APP}/api/auth/csrf"
ALLOCATIONS_ENDPOINT = f"{APP}/api/allocations"
SEATS_ENDPOINT = f"{APP}/api/seats"

class _NoData:
    pass
def _req(url, method="GET", body=None, headers=None):
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            code = r.status
            raw = r.read().decode("utf-8")
            return code, json.loads(raw) if raw else None, raw
    except urllib.error.HTTPError as e:
        _body = e.read().decode("utf-8")
        try:
            j = json.loads(_body)
        except Exception:
            j = _NoData
        return e.code, j, _body

def login():
    code, data, raw = _req(LOGIN_ENDPOINT, "POST",
                            {"username": "admin", "password": "admin"},
                            {"Content-Type": "application/json"})
    if not isinstance(data, dict) or data.get("role") != "ADMIN":
        print(f"[FAIL] login: HTTP {code} {data}"); sys.exit(1)
    _, data, _ = _req(CSRF_ENDPOINT)
    SESSION["token"] = data.get("token", "")
    SESSION["csrf"] = SESSION["token"]
    return SESSION["token"]



def _header(token):
    return {"X-XSRF-TOKEN": token}


def post(path, body):
    code, data_raw, raw = _req(path, "POST", body,
                              {"Content-Type": "application/json",
                               "X-XSRF-TOKEN": SESSION["token"]})
    if data_raw is None or not isinstance(data_raw, dict):
        data = {"message": str(data_raw) if data_raw else "no-data", "raw": raw[:400]}
    else:
        data = data_raw
    return code, data


def get(path, params=None):
    url = path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    code, data_raw, raw = _req(url)
    if data_raw is None:
        data = {"message": "no-data", "raw": raw[:200]}
    elif not isinstance(data_raw, dict):
        data = {"message": str(data_raw), "raw": raw[:200]}
    else:
        data = data_raw
    return code, data


def _header(token):
    """Return X-XSRF-TOKEN header with the given token value."""
    return {"X-XSRF-TOKEN": token if isinstance(token, str) else token.get("token", "")}


def post(path, body):
    code, data_raw, raw = _req(path, "POST", body,
                              {"Content-Type": "application/json",
                               "X-XSRF-TOKEN": SESSION["token"]})
    if data_raw is None or not isinstance(data_raw, dict):
        data = {"message": str(data_raw), "raw": raw[:400]}
    else:
        data = data_raw
    return code, data


def _header(token):
    """Return X-XSRF-TOKEN header with the given token value."""
    return {"X-XSRF-TOKEN": token["token"] if isinstance(token, dict) else token};
    code, data_raw, raw = _req(path, "POST", body,
                              {"Content-Type": "application/json",
                               "X-XSRF-TOKEN": SESSION["token"]})
    if data_raw is None or not isinstance(data_raw, dict):
        data = {"message": str(data_raw) if data_raw else "no-data", "raw": raw[:400]}
    else:
        data = data_raw
    return code, data


def get(path, params=None):
    url = path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    code, (data, raw) = _req(url)
    return code, data

def mk_exam(session, subject, branch, course, examDate, examTime):
    code, data = post(f"{APP}/api/exams", {
        "subjectName": subject, "branch": branch, "course": course,
        "examDate": examDate, "examTime": examTime,
    })
    return code, data


def exam_create(subject, branch, course, examDate, examTime):
    return mk_exam(SESSION, subject, branch, course, examDate, examTime)

def mk_room(session, roomNumber, names, studentsPerDesk, rows, cols):
    # We never want to duplicate a roomNumber; use the exam id to keep it unique.
    code, data = post(f"{APP}/api/rooms", {
        "roomNumber": f"QL-{session['exam_id']}-{names}",
        "studentsPerDesk": studentsPerDesk,
        "rowsCount": rows, "columnsCount": cols,
        "capacity": rows * cols * studentsPerDesk,
        "active": True,
    })
    return data

def generate(session, roomid, examid):
    code, data = post(f"{APP}/api/allocate", {"roomIds": [roomid], "examId": examid})
    return code, data


def get(url):
    code, data_raw, raw = _req(url)
    data = data_raw if isinstance(data_raw, dict) else None
    return code, data


def get_with_params(path, params):
    url = path + "?" + urllib.parse.urlencode(params)
    return get(url)

# ---- harness ----
sessions = {}
print("=== duplicate-seat guarantee test (two students, one exam, one room) ===\n")

# 1) Login
tkn = login()
SESSION["csrf"] = tkn
print(f"login ok, csrf token fetched")

# 2) Create a dedicated exam with a known, small eligibility audience    exam = mk_exam(SESSION, "Duplicate-Seat Guarantee Exam", "CS", "CS", "2027-01-01", "09:00")
print(f"exam created: {exam}")
if 'id' not in exam:
    print("[FAIL] exam creation returned no id - inspect the raw response above")
    sys.exit(1)
session["exam_id"] = exam["id"]
exam_id = exam["id"]

# 3) Create a dedicated room
room = mk_room(SESSION, "101", 10, 2, 2)
print(f"room created: {room}")
room_id = room["id"]

# 4) Generate the chart
code, data = generate(SESSION, room_id, exam_id)
print(f"allocation generated: HTTP {code} {data}")

# 5) Query the allocations
code, allocations = get(ALLOCATIONS_ENDPOINT, {"examId": exam_id})
if code != 200:
    print(f"[FAIL] GET /api/allocations: HTTP {code}"); sys.exit(1)

rows = allocations or []
print(f"\n{f'allocations':{18}}: {len(rows)}")
# Make sure we have the right exam and room data
exam_alloc = [a for a in rows if a.get("examId") == exam_id]
seat_ids = [a.get("seatId") for a in exam_alloc]
student_ids = [a.get("studentId") for a in exam_alloc]

# 6) Query the seats
code, seats = get(SEATS_ENDPOINT, {"examId": exam_id})
if code != 200:
    print(f"[FAIL] GET /api/seats: HTTP {code}"); sys.exit(1)

seat_rows = seats or []
print(f"{'seats fetched':{18}}: {len(seat_rows)}")

# 7) Check: no two allocations share the same seat_id for the same exam
dup_by_seat = {}
for a in exam_alloc:
    sid = a.get("seatId")
    if sid not in dup_by_seat:
        dup_by_seat[sid] = a.get("studentId")
    else:
        prev = dup_by_seat[sid]
        print(f"[FAIL] duplicate seat {sid}: {prev} and {a.get('studentId')} both on exam {exam_id}")
        sys.exit(1)

# 8) Check: no two allocations share the same student+seat+exam
seen = set()
for a in exam_alloc:
    key = (a.get("examId"), a.get("studentId"), a.get("seatId"))
    if key in seen:
        print(f"[FAIL] duplicate (exam, student, seat): {key}")
        sys.exit(1)
    seen.add(key)

# 9) Check: if a room holds N desks and each desk seats 1, at most N students
room_rows = [r for r in room_rows if r.get("roomId") == room_id]
if room_rows:
    r = room_rows[0]
else:
    r = {"rowsCount": rows, "columnsCount": cols, "studentsPerDesk": studentsPerDesk}
capacity = r["rowsCount"] * r["columnsCount"] * r["studentsPerDesk"]
SeatAllocation_rows = [a for a in exam_alloc if a.get("roomId") == room_id]
if len(SeatAllocation_rows) > capacity:
    print(f"[FAIL] more allocations ({len(SeatAllocation_rows)}) than room capacity ({capacity})")
    sys.exit(1)

# 10) Check: at most one student per seat
allocations_by_seat = {}
for a in SeatAllocation_rows:
    sid = a.get("seatId")
    if sid not in allocations_by_seat:
        allocations_by_seat[sid] = []
    allocations_by_seat[sid].append(a.get("studentId"))
dup_seat = [sid for sid, ids in allocations_by_seat.items() if len(set(ids)) > 1]
if dup_seat:
    print(f"[FAIL] duplicate seats: {dup_seat}")
    sys.exit(1)

print("\n=== PASS: no duplicate seats on exam", exam_id, "===")
0
