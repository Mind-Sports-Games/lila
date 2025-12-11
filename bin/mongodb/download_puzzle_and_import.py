import boto3
import os
import subprocess
import json

BUCKET = 'playstrategy-puzzles'
DB_MANIFEST_KEY = 'manifests/db_manifest.json'
LOG_FILE = 'puzzles.log'

def log(msg):
    print(msg)
    with open(LOG_FILE, 'a') as f:
        f.write(msg + '\n')

def load_manifest(s3, key):
    try:
        obj = s3.get_object(Bucket=BUCKET, Key=key)
        return json.loads(obj['Body'].read())
    except s3.exceptions.NoSuchKey:
        return {}

def save_manifest(s3, key, manifest):
    s3.put_object(Bucket=BUCKET, Key=key, Body=json.dumps(manifest, indent=2))

def update_manifest(manifest, variant, month, generator):
    manifest.setdefault(variant, {}).setdefault(month, [])
    if generator not in manifest[variant][month]:
        manifest[variant][month].append(generator)

def download_json_from_s3(s3_key, local_path, bucket=BUCKET):
    s3 = boto3.client('s3')
    with open(local_path, 'wb') as f:
        s3.download_fileobj(bucket, s3_key, f)
    log(f"Downloaded {s3_key} to {local_path}")

def add_var_puzzles_to_json(json_path):
    # Read the original JSON
    with open(json_path, 'r') as f:
        content = f.read()
    # Prepend 'var puzzles='
    with open(json_path, 'w') as f:
        f.write('var puzzles=' + content)
    log(f"Prepended 'var puzzles=' to {json_path}")

def process_and_update_manifest(variant, month, generator, mongo_url, mongosh_script):
    s3 = boto3.client('s3')
    prefix = f"puzzles/{variant}/{month}/{generator}/"
    response = s3.list_objects_v2(Bucket=BUCKET, Prefix=prefix)
    manifest = load_manifest(s3, DB_MANIFEST_KEY)
    files_processed = 0

    if 'Contents' in response:
        for obj in response['Contents']:
            s3_key = obj['Key']
            if s3_key.endswith('.json'):
                local_filename = os.path.basename(s3_key)
                download_json_from_s3(s3_key, local_filename)
                add_var_puzzles_to_json(local_filename)
                # Run your mongosh import script
                log(f"Running mongosh for {local_filename}")
                result = subprocess.run([
                    'mongosh', '--host', mongo_url, 'lichess',
                    '--eval', f"var jsonFile='{local_filename}'",
                    mongosh_script
                ], capture_output=True, text=True)
                log(result.stdout)
                log(result.stderr)
                if result.returncode == 0:
                    log(f"Imported {local_filename} successfully.")
                    files_processed += 1
                else:
                    log(f"Import failed for {local_filename}.")
                os.remove(local_filename)  # Clean up local file

    if files_processed > 0:
        update_manifest(manifest, variant, month, generator)
        save_manifest(s3, DB_MANIFEST_KEY, manifest)
        log(f"DB manifest updated for {variant} {month} {generator}")

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Download puzzles from S3 and import into MongoDB using mongosh.")
    parser.add_argument("variant", help="Variant name (e.g. linesOfAction)")
    parser.add_argument("month", help="Month (e.g. 2025-11)")
    parser.add_argument("generator", help="Generator name (e.g. brute-force-endgame)")
    parser.add_argument("mongo_url", help="MongoDB connection URL (e.g. localhost:27017)")
    parser.add_argument("mongosh_script", help="Path to mongosh import script (e.g. importPuzzle.mjs)")
    args = parser.parse_args()

    process_and_update_manifest(args.variant, args.month, args.generator, args.mongo_url, args.mongosh_script)

#usage:
#python3 download_puzzle_and_import.py atomic 2022-11 brute-force-endgame localhost:27017 importPuzzle.mjs