- always run scalafmt before a commit
- ## Functional Programming Principles

  - **NEVER use try/catch blocks** - This is an antipattern in functional programming
  - Always use `Either[Error, Result]` for error handling (already established in this codebase with `ApicurioResult[T]`)
  - Use `Option` for nullable values
  - Avoid throwing exceptions - return functional error types instead
  - Follow strict functional programming principles in all implementations