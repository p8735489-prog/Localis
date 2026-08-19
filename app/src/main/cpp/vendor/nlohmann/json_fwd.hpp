// Compatibility forward-header for the bundled nlohmann/json 3.12.0.
// The project ships the complete json.hpp. Including it here keeps every
// consumer on the exact same ABI namespace and avoids clashes between a
// hand-written forward declaration and the full nlohmann implementation.

#pragma once

#include "json.hpp"
