// nlohmann/json 3.12.0 forward declarations.
// Keep this header compatible with the bundled json.hpp ABI namespace.

#ifndef INCLUDE_NLOHMANN_JSON_FWD_HPP_
#define INCLUDE_NLOHMANN_JSON_FWD_HPP_

#include <cstdint>
#include <map>
#include <memory>
#include <string>
#include <vector>

#ifndef JSON_SKIP_LIBRARY_VERSION_CHECK
    #if defined(NLOHMANN_JSON_VERSION_MAJOR) && defined(NLOHMANN_JSON_VERSION_MINOR) && defined(NLOHMANN_JSON_VERSION_PATCH)
        #if NLOHMANN_JSON_VERSION_MAJOR != 3 || NLOHMANN_JSON_VERSION_MINOR != 12 || NLOHMANN_JSON_VERSION_PATCH != 0
            #warning "Already included a different version of the library!"
        #endif
    #endif
#endif

#define NLOHMANN_JSON_VERSION_MAJOR 3
#define NLOHMANN_JSON_VERSION_MINOR 12
#define NLOHMANN_JSON_VERSION_PATCH 0

#ifndef JSON_DIAGNOSTICS
    #define JSON_DIAGNOSTICS 0
#endif
#ifndef JSON_DIAGNOSTIC_POSITIONS
    #define JSON_DIAGNOSTIC_POSITIONS 0
#endif
#ifndef JSON_USE_LEGACY_DISCARDED_VALUE_COMPARISON
    #define JSON_USE_LEGACY_DISCARDED_VALUE_COMPARISON 0
#endif
#if JSON_DIAGNOSTICS
    #define NLOHMANN_JSON_ABI_TAG_DIAGNOSTICS _diag
#else
    #define NLOHMANN_JSON_ABI_TAG_DIAGNOSTICS
#endif
#if JSON_DIAGNOSTIC_POSITIONS
    #define NLOHMANN_JSON_ABI_TAG_DIAGNOSTIC_POSITIONS _dp
#else
    #define NLOHMANN_JSON_ABI_TAG_DIAGNOSTIC_POSITIONS
#endif
#if JSON_USE_LEGACY_DISCARDED_VALUE_COMPARISON
    #define NLOHMANN_JSON_ABI_TAG_LEGACY_DISCARDED_VALUE_COMPARISON _ldvcmp
#else
    #define NLOHMANN_JSON_ABI_TAG_LEGACY_DISCARDED_VALUE_COMPARISON
#endif

#ifndef NLOHMANN_JSON_NAMESPACE_NO_VERSION
    #define NLOHMANN_JSON_NAMESPACE_NO_VERSION 0
#endif

#define NLOHMANN_JSON_ABI_TAGS_CONCAT_EX(a, b, c) json_abi ## a ## b ## c
#define NLOHMANN_JSON_ABI_TAGS_CONCAT(a, b, c) NLOHMANN_JSON_ABI_TAGS_CONCAT_EX(a, b, c)
#define NLOHMANN_JSON_ABI_TAGS \
    NLOHMANN_JSON_ABI_TAGS_CONCAT( \
        NLOHMANN_JSON_ABI_TAG_DIAGNOSTICS, \
        NLOHMANN_JSON_ABI_TAG_LEGACY_DISCARDED_VALUE_COMPARISON, \
        NLOHMANN_JSON_ABI_TAG_DIAGNOSTIC_POSITIONS)


#define NLOHMANN_JSON_NAMESPACE_CONCAT_EX(a, b) a ## b
#define NLOHMANN_JSON_NAMESPACE_CONCAT(a, b) NLOHMANN_JSON_NAMESPACE_CONCAT_EX(a, b)

#ifndef NLOHMANN_JSON_NAMESPACE
    #define NLOHMANN_JSON_NAMESPACE \
        nlohmann::NLOHMANN_JSON_NAMESPACE_CONCAT(NLOHMANN_JSON_ABI_TAGS, _v3_12_0)
#endif

#ifndef NLOHMANN_JSON_NAMESPACE_BEGIN
    #define NLOHMANN_JSON_NAMESPACE_BEGIN \
        namespace nlohmann { inline namespace NLOHMANN_JSON_NAMESPACE_CONCAT( \
            NLOHMANN_JSON_ABI_TAGS, _v3_12_0) {
#endif

#ifndef NLOHMANN_JSON_NAMESPACE_END
    #define NLOHMANN_JSON_NAMESPACE_END } }
#endif

NLOHMANN_JSON_NAMESPACE_BEGIN

template<typename T, typename SFINAE = void>
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

#endif // INCLUDE_NLOHMANN_JSON_FWD_HPP_
