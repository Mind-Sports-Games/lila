import csv
import sys
import json

# Usage: python json_to_csv.py input.json output.csv

if len(sys.argv) != 3:
    print(f"Usage: {sys.argv[0]} input.json output.csv")
    sys.exit(1)

input_file = sys.argv[1]
output_file = sys.argv[2]

# Order of columns as specified
columns = [
    "lib", "variant",
    "num_tournaments", "games_count",
    "bot_count", "bot_rate", "human_count",
    "avg_t", "std_t",
    "timeout_count", "timeout_rate",
    "berserk_count", "berserk_rate"
]

with open(input_file) as f:
    data = json.load(f)

with open(output_file, "w", newline='') as f:
    writer = csv.DictWriter(f, fieldnames=columns)
    writer.writeheader()
    for row in data:
        writer.writerow({col: row.get(col, "") for col in columns})

print(f"Wrote {len(data)} records to {output_file}")
