# Repository Agent Instructions

## Git Commit Attribution

When OpenCode creates a commit or materially contributes to the changes in a commit, preserve the following attribution:

- Human author and committer: `Codename: Рукоблуд <poprekolu147@gmail.com>`
- OpenCode co-author trailer: `Co-authored-by: OpenCode <opencode-agent[bot]@users.noreply.github.com>`

Create commits with the human identity as both the Git author and committer, and add the OpenCode trailer as a separate paragraph in the commit message. For example:

```text
docs: describe the change

Co-authored-by: OpenCode <opencode-agent[bot]@users.noreply.github.com>
```

Use a per-command Git configuration so the identity is applied to the commit without changing the user's global Git configuration:

```bash
git -c user.name="Codename: Рукоблуд" \
    -c user.email="poprekolu147@gmail.com" \
    commit -m "type: describe the change" \
    -m "Co-authored-by: OpenCode <opencode-agent[bot]@users.noreply.github.com>"
```

Rules:

- Do not replace the human author with OpenCode.
- Do not use only `--author`: the human identity should also be the committer.
- Do not change global Git configuration.
- Do not omit or alter the OpenCode trailer when OpenCode contributed to the commit.
- Add the trailer only when OpenCode actually contributed to that commit.
- Do not rewrite existing commits solely to change attribution unless the user explicitly asks.
- Work on a feature branch; do not commit directly to `master`.
- Before creating a commit, inspect the staged diff and ensure it contains only the intended changes.

GitHub associates commit identities with accounts based on email addresses. The human email above must be verified on the user's GitHub account for the commit to be associated with that account. Recognition of the OpenCode noreply address depends on GitHub's account association for that address.
