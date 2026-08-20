# vendor/CMakeLists.txt
# Wraps upstream llama.cpp vendor subprojects so the parent CMakeLists.txt can
# add_subdirectory(vendor) once, before common/, ensuring cpp-httplib is defined
# before common/CMakeLists.txt links against it.

# license_add_file() is defined in upstream llama.cpp's cmake/common.cmake but
# is not available in this Android build. Stub it so vendor/cpp-httplib/CMakeLists.txt
# can call it without error.
if(NOT COMMAND license_add_file)
    function(license_add_file name file)
        # No-op: license tracking is not used in the Android build.
    endfunction()
endif()

add_subdirectory(cpp-httplib)
