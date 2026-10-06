#!/usr/bin/env bash
# Seeds the baseline test data used by cdp-test.mjs against a running instance.
B=${1:-http://localhost:8081/api}

for s in '101|Ananya Sharma|CSE|S3' '102|Rahul|ECE|S3' '103|Priya Sharma|CSE|s3' '104|Karthik|MECH|S3'; do
  IFS='|' read -r r n c m <<< "$s"
  curl -s -X POST "$B/students" -H 'Content-Type: application/json' \
    -d "{\"rollNo\":\"$r\",\"name\":\"$n\",\"course\":\"$c\",\"currentSemester\":\"$m\"}" -o /dev/null
done
curl -s -X POST "$B/rooms" -H 'Content-Type: application/json' \
  -d '{"roomNumber":"121","capacity":20,"rowsCount":5,"columnsCount":3}' -o /dev/null
curl -s -X POST "$B/rooms" -H 'Content-Type: application/json' \
  -d '{"roomNumber":"122","capacity":30,"rowsCount":6,"columnsCount":5}' -o /dev/null
curl -s -X POST "$B/exams" -H 'Content-Type: application/json' \
  -d '{"subjectName":"Data Structures","semester":"S3","examDate":"2026-11-02","examTime":"10:00","applicationId":"APP-S3-20261102-001"}' -o /dev/null

# baseline chart
curl -s -X POST "$B/allocate" -H 'Content-Type: application/json' -d '{"examId":1}' -o /dev/null

# baseline import files (CSV + XLSX + PDF fixtures)
cd "$(dirname "$0")" || exit 1
curl -s -X POST "$B/import/students" -F "file=@test-students.csv" -o /dev/null
curl -s -X POST "$B/import/students" -F "file=@test-students.xlsx" -o /dev/null
curl -s -X POST "$B/import/students" -F "file=@test-students.pdf" -o /dev/null

echo "seeded: $(curl -s "$B/students")" | python -c "import sys,json;print('students:',len(json.loads(sys.stdin.read().split('seeded: ')[1])))"
