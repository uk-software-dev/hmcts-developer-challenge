# HMCTS Dev Test Backend
This will be the backend for the brand new HMCTS case management system. As a potential candidate we are leaving
this in your hands. Please refer to the brief for the complete list of tasks! Complete as much as you can and be
as creative as you want.

You should be able to run `./gradlew build` to start with to ensure it builds successfully. Then from that you
can run the service in IntelliJ (or your IDE of choice) or however you normally would.

There is an example endpoint provided to retrieve an example of a case. You are free to add/remove fields as you
wish.

## Backend tests

The backend uses JUnit 5. From the `backend` directory, run the unit and integration tests with:

```bash
./gradlew test integration
```

Unit tests cover task service validation and status handling, and in-memory repository create, lookup, update, and
delete behavior. Integration tests exercise the HTTP endpoints for listing, retrieving, creating, updating the
status of, and deleting tasks, along with invalid requests, missing tasks, the example case endpoint, and the
welcome endpoint.

The functional and smoke tests start the service using the configured port (`4000`). To include those suites in
verification, set their request URL accordingly:

```bash
TEST_URL=http://localhost:4000 ./gradlew check -x checkstyleMain functional smoke
```

`checkstyleMain` is excluded in this command because the existing main sources have Checkstyle violations. The
task repository is currently in-memory, so tests verify behavior only during the running application; task changes
are not durable across restarts. The example case endpoint returns fixed data and does not persist cases.
