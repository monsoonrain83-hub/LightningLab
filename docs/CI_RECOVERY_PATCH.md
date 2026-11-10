# CI recovery patch

The workflow now patches the three Float-to-Int timeline ruler calls before compilation and excludes `.github/workflows` from the extracted ZIP commit, avoiding the GitHub App `workflows` permission rejection. The exact post-extraction source is uploaded as an artifact even when the build fails.
