// nlohmann/json 3.12.0 compatible forward declarations.
// Kept in sync with the bundled json.hpp so nlohmann::json resolves to the
// same versioned ABI namespace when this header is included first.

#pragma once

#include <cstdint>
#include <map>
#include <memory>
#include <string>
#include <vector>

#ifndef NLOHMANN_JSON_VERSION_MAJOR
#define NLOHMANN_JSON_VERSION_MAJOR 3
#endif
#ifndef NLOHMANN_JSON_VERSION_MINOR
#define NLOHMANN_JSON_VERSION_MINOR 12
#endif
#ifndef NLOHMANN_JSON_VERSION_PATCH
#define NLOHMANN_JSON_VERSION_PATCH 0
#endif

#ifndef NLOHMANN_JSON_NAMESPACE_NO_VERSION
#define NLOHMANN_JSON_NAMESPACE_NO_VERSION 0
#endif

#define NLOHMANN_JSON_ABI_TAGS_CONCAT_EX(a,b,c) json_abi##a##b##c
#define NLOHMANN_JSON_ABI_TAGS_CONCAT(a,b,c) NLOHMANN_JSON_ABI_TAGS_CONCAT_EX(a,b,c)
#define NLOHMANN_JSON_ABI_TAGS json_abi0_0_0

#define NLOHMANN_JSON_NAMESPACE_VERSION _v3_12_0
#define NLOHMANN_JSON_NAMESPACE_CONCAT_EX(a,b) a##b
#define NLOHMANN_JSON_NAMESPACE_CONCAT(a,b) NLOHMANN_JSON_NAMESPACE_CONCAT_EX(a,b)

#ifndef NLOHMANN_JSON_NAMESPACE_BEGIN
#define NLOHMANN_JSON_NAMESPACE_BEGIN \
    namespace nlohmann { inline namespace json_abi_v3_12_0 {
#endif
#ifndef NLOHMANN_JSON_NAMESPACE_END
#define NLOHMANN_JSON_NAMESPACE_END } }
#endif

NLOHMANN_JSON_NAMESPACE_BEGIN

template<typename T = void, typename SFINAE = void>
struct adl_serializer;

template<
    template<typename, typename, typename...> class ObjectType = std::map,
    template<typename, typename...> class ArrayType = std::vector,
    class StringType = std::string,
    class BooleanType = bool,
    class NumberIntegerType = std::int64_t,
    class NumberUnsignedType = std::uint64_t,
    class NumberFloatType = double,
    template<typename> class AllocatorType = std::allocator,
    template<typename, typename = void> class JSONSerializer = adl_serializer,
    class BinaryType = std::vector<std::uint8_t>,
    class CustomBaseClass = void>
class basic_json;

template<typename RefStringType>
class json_pointer;

using json = basic_json<>;

template<class Key, class T, class IgnoredLess, class Allocator>
struct ordered_map;

using ordered_json = basic_json<nlohmann::ordered_map>;

NLOHMANN_JSON_NAMESPACE_END
