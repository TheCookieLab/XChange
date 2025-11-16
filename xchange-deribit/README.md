## Running integration tests that require API keys

Integration tests that require API keys read them from environment variables. They can be defined in `integration-test.env.properties`. Sample content can be found in `example.integration-test.env.properties`.

If no keys are provided the integration tests that need them are skipped.

> [!CAUTION]
> Never commit your api credentials to the repository!

