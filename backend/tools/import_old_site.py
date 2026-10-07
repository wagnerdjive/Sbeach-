#!/usr/bin/env python3
"""Imports the past events, their photo albums and the loose space photos of the previous southbeach.co.mz site into the staff API.

    python3 import_old_site.py --api http://localhost:8080 --user admin --password '<APP_ADMIN_PASSWORD>'

Photos are downloaded from the old site (resized to at most 1600 px, JPEG), uploaded as media files and attached to each event's album.
Safe to run again: events that already exist are not duplicated, and an interrupted album continues where it stopped.
"""
import argparse, base64, json, os, sys, urllib.request, urllib.error, uuid
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))


def call(args, method, path, body=None, raw=None, content_type=None):
    headers = {"Authorization": "Basic " + base64.b64encode(f"{args.user}:{args.password}".encode()).decode()}
    data = raw
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if content_type:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(args.api.rstrip("/") + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            payload = response.read()
            return json.loads(payload) if payload else None
    except urllib.error.HTTPError as error:
        raise SystemExit(f"{method} {path} failed: {error.code} {error.read().decode(errors='replace')[:300]}")


def download(name):
    url = f"https://static.wixstatic.com/media/{name}/v1/fit/w_1600,h_1600,q_82/photo.jpg"
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=60) as response:
                return response.read()
        except Exception:
            if attempt == 2:
                raise


def upload(args, image):
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").encode() \
        + image + f"\r\n--{boundary}--\r\n".encode()
    return call(args, "POST", "/api/admin/media", raw=body, content_type=f"multipart/form-data; boundary={boundary}")["url"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", default="http://localhost:8080")
    parser.add_argument("--user", default="admin")
    parser.add_argument("--password", required=True)
    args = parser.parse_args()
    events = json.load(open(os.path.join(HERE, "old-site-events.json"), encoding="utf-8"))["events"]
    existing = {e["titlePt"]: e for e in call(args, "GET", "/api/admin/past-events")}
    for event in events:
        fields = {k: event.get(k) for k in ("titlePt", "titleEn", "dateTextPt", "dateTextEn", "timeText", "location", "descriptionPt")}
        current = existing.get(event["titlePt"])
        if current is None:
            current = call(args, "POST", "/api/admin/past-events", body={**fields, "visible": True})
            print(f"+ {event['titlePt']}")
        done = current["photoCount"]
        todo = event["photos"][done:]
        if not todo:
            print(f"= {event['titlePt']} ({done} photos already there)")
            continue
        with ThreadPoolExecutor(6) as pool:
            images = pool.map(download, todo)  # downloaded in parallel, consumed in order
            for index, image in enumerate(images, start=done + 1):
                url = upload(args, image)
                call(args, "POST", f"/api/admin/past-events/{current['id']}/photos", body={"imageUrl": url})
                if index % 10 == 0 or index == len(event["photos"]):
                    print(f"   {event['titlePt']}: {index}/{len(event['photos'])}")
    # Keep the order of the file (newest first) for the events this script manages; any other events stay after them.
    current = call(args, "GET", "/api/admin/past-events")
    wanted = [e["titlePt"] for e in events]
    managed = sorted((e for e in current if e["titlePt"] in wanted), key=lambda e: wanted.index(e["titlePt"]))
    others = [e for e in current if e["titlePt"] not in wanted]
    call(args, "PUT", "/api/admin/past-event-order", body={"ids": [e["id"] for e in managed + others]})
    # Space and drink photos that only appeared on pages of the old site go to the managed gallery.
    gallery = json.load(open(os.path.join(HERE, "old-site-events.json"), encoding="utf-8")).get("gallery", [])
    have = {p["captionPt"] for p in call(args, "GET", "/api/admin/gallery")}
    todo = [g for g in gallery if g["captionPt"] not in have]
    with ThreadPoolExecutor(6) as pool:
        for item, image in zip(todo, pool.map(lambda g: download(g["photo"]), todo)):
            url = upload(args, image)
            call(args, "POST", "/api/admin/gallery", body={"imageUrl": url, "category": item["category"], "size": item["size"],
                                                           "captionPt": item["captionPt"], "captionEn": item["captionEn"], "visible": True})
            print(f"+ gallery: {item['captionPt']}")
    print("Done.")


if __name__ == "__main__":
    sys.exit(main())
