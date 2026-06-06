#!/usr/bin/env python3
"""
Discord notification script for JCraft Maven deployments.
Called by adnanh/webhook when a new release jar is deployed to Reposilite.

Usage: discord-notify.py <config_file> <artifact_path> <deployed_by>
  config_file    Path to the .conf file for this notification target, e.g.
                 /opt/scripts/discord-notify-beta.conf
  artifact_path  Full path from the Reposilite deploy event, e.g.
                 /releases/net/arna/jcraft-fabric/0.18.0+b13/jcraft-fabric-0.18.0+b13.jar
  deployed_by    Access token name that performed the deployment.

Passing the config file as an argument lets a single script installation serve
multiple webhooks (e.g. one Discord channel per server, each with its own .conf).

Available placeholders in the message template:
  {artifact}      Artifact ID, e.g. jcraft-fabric
  {version}       Version string, e.g. 0.18.0+b13
  {filename}      Full filename, e.g. jcraft-fabric-0.18.0+b13.jar
  {by}            Access token that deployed the artifact
  {download_url}  Direct download URL for the jar, e.g.
                  https://maven.jcraft-eoe.com/releases/net/arna/jcraft-fabric/0.18.0+b13/jcraft-fabric-0.18.0+b13.jar
  {compare_url}   GitHub compare URL against the previous tag, e.g.
                  https://github.com/org/repo/compare/v0.18.0+b12...v0.18.0+b13
                  Falls back to the individual release/tag URL if no previous tag is found.
  {compare_range} Short form of the comparison, e.g. v0.18.0+b12...v0.18.0+b13
                  Useful as link text: [{compare_range}]({compare_url})
                  Falls back to just the current tag if no previous tag is found.
"""

import configparser
import json
import re
import sys

import requests


def get_previous_tag(current_tag: str, repo: str, token: str | None) -> str | None:
    """
    Return the tag that immediately precedes *current_tag* in GitHub's tag list
    (which is ordered newest-first by the commit the tag points to).
    Returns None if *current_tag* is the oldest tag or cannot be found.
    """
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"

    page     = 1
    per_page = 100

    while True:
        resp = requests.get(
            f"https://api.github.com/repos/{repo}/tags",
            headers = headers,
            params  = {"per_page": per_page, "page": page},
            timeout = 10,
        )
        resp.raise_for_status()
        tags = resp.json()

        if not tags:
            return None  # exhausted all pages without finding the tag

        for i, tag in enumerate(tags):
            if tag["name"] == current_tag:
                if i + 1 < len(tags):
                    # Previous tag is on the same page
                    return tags[i + 1]["name"]

                # Current tag is the last entry on this page;
                # fetch the first entry of the next page.
                resp2 = requests.get(
                    f"https://api.github.com/repos/{repo}/tags",
                    headers = headers,
                    params  = {"per_page": per_page, "page": page + 1},
                    timeout = 10,
                )
                resp2.raise_for_status()
                next_page = resp2.json()
                return next_page[0]["name"] if next_page else None

        page += 1


def main() -> None:
    if len(sys.argv) < 4:
        print(f"Usage: {sys.argv[0]} <config_file> <artifact_path> <deployed_by>", file=sys.stderr)
        sys.exit(1)

    config_file   = sys.argv[1]
    artifact_path = sys.argv[2]
    deployed_by   = sys.argv[3]

    cfg = configparser.RawConfigParser()
    if not cfg.read(config_file):
        print(f"Could not read config file: {config_file}", file=sys.stderr)
        sys.exit(1)

    webhook_url      = cfg.get("discord", "webhook_url")
    username         = cfg.get("discord", "username")
    avatar_url       = cfg.get("discord", "avatar_url")
    message_template = cfg.get("discord", "message")
    github_repo      = cfg.get("github", "repo")
    # Token is optional: useful for private repos or to raise the rate limit
    # from 60 to 5 000 requests/hour. Leave blank for unauthenticated access.
    github_token     = cfg.get("github", "token", fallback=None) or None
    reposilite_base  = cfg.get("reposilite", "base_url").rstrip("/")

    # --- Derive fields from the artifact path ---
    # artifact_path is e.g. /releases/net/arna/jcraft-fabric/0.18.0+b13/jcraft-fabric-0.18.0+b13.jar
    parts    = artifact_path.strip("/").split("/")
    filename = parts[-1]   # jcraft-fabric-0.18.0+b13.jar
    version  = parts[-2]   # 0.18.0+b13  (directory name == bare version, no parsing needed)

    # Strip .jar then remove the -<version> suffix (version always starts with a digit)
    stem        = filename.removesuffix(".jar")
    artifact_id = re.sub(r"-\d.*$", "", stem)   # jcraft-fabric

    download_url = f"{reposilite_base}{artifact_path}"

    # --- Build the GitHub compare URL ---
    current_tag = f"v{version}"
    try:
        prev_tag = get_previous_tag(current_tag, github_repo, github_token)
    except requests.RequestException as exc:
        print(f"Warning: GitHub API request failed ({exc}); compare URL will fall back to tag URL", file=sys.stderr)
        prev_tag = None

    if prev_tag:
        compare_url   = f"https://github.com/{github_repo}/compare/{prev_tag}...{current_tag}"
        compare_range = f"{prev_tag}...{current_tag}"
    else:
        # No previous tag found (first release, or the API call failed)
        compare_url   = f"https://github.com/{github_repo}/releases/tag/{current_tag}"
        compare_range = current_tag
        print(f"Info: no previous tag found for {current_tag}, using tag URL as fallback", file=sys.stderr)

    # --- Format and send the Discord message ---
    # Support \n escape sequences in the config value for multi-line messages
    message = message_template.replace("\\n", "\n").format(
        artifact      = artifact_id,
        version       = version,
        filename      = filename,
        by            = deployed_by,
        download_url  = download_url,
        compare_url   = compare_url,
        compare_range = compare_range,
    )

    resp = requests.post(
        webhook_url,
        json    = {"username": username, "avatar_url": avatar_url, "content": message},
        timeout = 10,
    )

    # Discord returns 204 No Content on success; 200 is also acceptable
    if not resp.ok:
        print(f"Discord request failed: HTTP {resp.status_code} {resp.text}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
