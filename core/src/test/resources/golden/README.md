# Golden File Cases

These cases are synthetic. They imitate the tone and structure of Finnish/EU
job postings, but the company names and postings are fictional.

Each case contains:

- `input.txt`: the job posting text passed to `AtsChecker.check(String, Profile)`
- `expected.json`: the expected verdict, hard-filter stop flag, findings, and skill gap

Future maintainers may replace or extend these with anonymized real postings.
When adding a real posting, remove identifying details, keep the original
phrasing that matters for rule behavior, and add the matching `expected.json`.
The Java golden test discovers case directories automatically, so no test code
change is needed for new cases.
