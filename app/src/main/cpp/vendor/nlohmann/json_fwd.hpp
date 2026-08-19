// Forward declaration wrapper for nlohmann/json.
// This project vendors the single-header json.hpp; json_fwd.hpp simply
// re-includes it so that both #include <nlohmann/json_fwd.hpp> and
// #include <nlohmann/json.hpp> resolve to the same definitions.
// This prevents "template parameter redefines default argument" errors
// that occur when two separate files declare the same template defaults.

#ifndef INCLUDE_NLOHMANN_JSON_FWD_HPP_
#define INCLUDE_NLOHMANN_JSON_FWD_HPP_

#include "json.hpp"

#endif  // INCLUDE_NLOHMANN_JSON_FWD_HPP_
