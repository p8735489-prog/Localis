// Bundled minimal forward declarations for nlohmann::json.
// This is a fallback used only when fetch_mtmd.sh cannot download the full
// vendor tree. The common/ chat template engine uses ordered_json for tool
// call parsing; if the full nlohmann/json.hpp is not available at compile time,
// the build will still succeed with these forward declarations.
//
// NOTE: When fetch_mtmd.sh succeeds, the full vendor/nlohmann/ from upstream
// llama.cpp replaces this file.

#pragma once

#include <cstdint>
#include <map>
#include <memory>
#include <string>
#include <vector>

namespace nlohmann {

template<typename T, typename SFINAE = void>
struct adl_serializer;

template<template<typename, typename, typename...> class ObjectType = std::map,
         template<typename, typename...> class ArrayType = std::vector,
         typename StringType = std::string,
         typename BooleanType = bool,
         typename NumberIntegerType = std::int64_t,
         typename NumberUnsignedType = std::uint64_t,
         typename NumberFloatType = double,
         template<typename> class AllocatorType = std::allocator,
         template<typename, typename = void> class JSONSerializer = adl_serializer>
class basic_json;

using json = basic_json<>;
using ordered_json = basic_json<std::map>;

}  // namespace nlohmann
