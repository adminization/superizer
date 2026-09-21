#!/usr/bin/env bash
#
# What version the next publish should carry, asked of the registry rather than of a human.
#
#   resolve-version.sh <channel> <artifact-path> <floor> [sha]
#
# The branch decides the channel and the registry decides the number, which is Adminizer's scheme
# (`.github/workflows/publish.yml` there) ported to Maven. Nobody edits a version to cut a release
# and no tag has to agree with a file: what is already published is the fact, and the version in
# `gradle.properties` is only a floor — raise it to start a new series, and it wins until the
# registry catches up.
#
#   release   the highest released version, patch bumped, but never below the floor
#   next      <base>-next.<n>,   n one past the highest -next already published
#   alpha     <base>-alpha.<n>,  the same, on its own counter
#   commit    <floor>-commit.<sha>, unique by construction and never bumped
#
# Two deviations from Adminizer's, both forced by Maven:
#
#   - npm has dist-tags, so `next` and `alpha` can both be `-build.N` and be told apart by the tag
#     they are published under. A Maven version string is the only channel there is, so the
#     channel is in it.
#   - the build counter resets when the base moves. Adminizer's keeps counting, which is harmless
#     there and merely confusing: `5.3.0-build.7` reads like the seventh build of 5.3.0 when it is
#     the first.
#
# Prints the version to stdout and nothing else, so it can be read into a variable.
set -euo pipefail

channel=${1:?channel: release | next | alpha | commit}
artifact=${2:?artifact path, e.g. cx/m42/superizer/core}
floor=${3:?floor version, e.g. 0.1.0}
sha=${4:-}

slug=${SUPERIZER_GITHUB_SLUG:-adminization/superizer}
user=${GPR_USER:-${GITHUB_ACTOR:-}}
token=${GPR_TOKEN:-${GITHUB_TOKEN:-}}

# Every version the registry has for this artifact, one per line.
#
# A failure here is deliberately indistinguishable from "nothing published yet": the first publish
# of a new artifact answers 404, and an outage answering 500 must not silently reuse a version that
# is already taken — but it cannot, because the registry rejects a duplicate. So the safe reading
# of "no answer" is "start from the floor", and the publish either succeeds or is refused.
published() {
  if [ -n "${SUPERIZER_VERSIONS_FILE:-}" ]; then
    cat "$SUPERIZER_VERSIONS_FILE"
    return
  fi
  curl -sSL --fail-with-body -u "$user:$token" \
    "https://maven.pkg.github.com/$slug/$artifact/maven-metadata.xml" 2>/dev/null |
    grep -oE '<version>[^<]+' | cut -c10- || true
}

# `sort -V` is only trustworthy within one shape. It reads 0.1.1 as *older* than 0.1.1-next.0 —
# shorter string first — which is the opposite of what a prerelease means, so releases and
# prereleases are never sorted against each other below.
highest() { sort -V -r | head -n1; }

versions=$(published)
releases=$(printf '%s\n' "$versions" | grep -E '^[0-9]+(\.[0-9]+)*$' || true)

case "$channel" in
  release)
    latest=$(printf '%s\n' "$releases" | highest)
    if [ -z "$latest" ]; then
      echo "$floor"
    else
      bumped=$(echo "$latest" | awk -F. -v OFS=. '{ $NF += 1; print }')
      printf '%s\n%s\n' "$bumped" "$floor" | highest
    fi
    ;;

  next | alpha)
    # Highest first by base, then by counter — sorted as two fields so that -next.10 beats -next.9.
    previous=$(printf '%s\n' "$versions" |
      grep -E "^[0-9]+(\.[0-9]+)*-$channel\.[0-9]+$" |
      sort -t- -k1,1V -k2,2V |
      tail -n1 || true)

    if [ -z "$previous" ]; then
      echo "$floor-$channel.0"
    else
      previousBase=${previous%%-*}
      previousCount=${previous##*.}
      base=$(printf '%s\n%s\n' "$previousBase" "$floor" | highest)
      if [ "$base" = "$previousBase" ]; then
        echo "$base-$channel.$((previousCount + 1))"
      else
        # The floor moved past what is published: a new series, counting from zero again.
        echo "$base-$channel.0"
      fi
    fi
    ;;

  commit)
    [ -n "$sha" ] || { echo "resolve-version.sh: the commit channel needs a sha" >&2; exit 1; }
    echo "$floor-commit.$sha"
    ;;

  *)
    echo "resolve-version.sh: unknown channel '$channel'" >&2
    exit 1
    ;;
esac
