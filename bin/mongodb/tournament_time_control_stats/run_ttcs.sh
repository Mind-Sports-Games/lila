#!/bin/bash
set -e

mongosh --host 172.31.13.43:27017 lichess tournament_time_control_stats.js > output.json
python3 ttcs_json_to_csv.py output.json ttcs.csv
rm output.json
