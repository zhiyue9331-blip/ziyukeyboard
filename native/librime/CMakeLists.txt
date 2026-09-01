cmake_minimum_required(VERSION 3.22)
project(ziyu_rime LANGUAGES C CXX)

if(NOT DEFINED RIME_SOURCE OR NOT DEFINED BOOST_SOURCE)
  message(FATAL_ERROR "RIME_SOURCE and BOOST_SOURCE are required")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)

# Only Boost.Regex has compiled objects; the remaining Boost use is header-only.
set(BOOST_INCLUDE_LIBRARIES regex CACHE STRING "" FORCE)
add_subdirectory("${BOOST_SOURCE}" boost EXCLUDE_FROM_ALL)

set(YAML_CPP_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(YAML_CPP_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(YAML_CPP_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory("${RIME_SOURCE}/deps/yaml-cpp" yaml-cpp EXCLUDE_FROM_ALL)

set(LEVELDB_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LEVELDB_BUILD_BENCHMARKS OFF CACHE BOOL "" FORCE)
set(LEVELDB_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory("${RIME_SOURCE}/deps/leveldb" leveldb EXCLUDE_FROM_ALL)

set(ENABLE_TOOLS OFF CACHE BOOL "" FORCE)
add_subdirectory("${RIME_SOURCE}/deps/marisa-trie" marisa EXCLUDE_FROM_ALL)

set(ENABLE_GTEST OFF CACHE BOOL "" FORCE)
set(ENABLE_BENCHMARK OFF CACHE BOOL "" FORCE)
set(BUILD_DOCUMENTATION OFF CACHE BOOL "" FORCE)
set(BUILD_PYTHON OFF CACHE BOOL "" FORCE)
set(USE_SYSTEM_MARISA ON CACHE BOOL "" FORCE)
set(LIBMARISA marisa CACHE STRING "" FORCE)
add_subdirectory("${RIME_SOURCE}/deps/opencc" opencc EXCLUDE_FROM_ALL)

# OpenCC's build target does not expose an include tree in the form librime expects.
set(OPENCC_GENERATED_INCLUDE "${CMAKE_BINARY_DIR}/generated/include")
file(MAKE_DIRECTORY "${OPENCC_GENERATED_INCLUDE}/opencc")
file(GLOB OPENCC_PUBLIC_HEADERS "${RIME_SOURCE}/deps/opencc/src/*.hpp")
file(COPY ${OPENCC_PUBLIC_HEADERS} DESTINATION "${OPENCC_GENERATED_INCLUDE}/opencc")
file(COPY "${CMAKE_BINARY_DIR}/opencc/src/opencc_config.h"
     DESTINATION "${OPENCC_GENERATED_INCLUDE}/opencc")

list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
set(BUILD_TEST OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC ON CACHE BOOL "" FORCE)
set(ENABLE_LOGGING OFF CACHE BOOL "" FORCE)
set(BUILD_DATA OFF CACHE BOOL "" FORCE)
set(ENABLE_TIMESTAMP OFF CACHE BOOL "" FORCE)
add_subdirectory("${RIME_SOURCE}" librime EXCLUDE_FROM_ALL)
target_compile_options(rime-static PRIVATE -Wno-error=deprecated-declarations)

add_library(ziyu_rime SHARED rime_jni.cpp)
target_include_directories(ziyu_rime PRIVATE
  "${RIME_SOURCE}/src"
  "${CMAKE_BINARY_DIR}/librime/src")
target_link_libraries(ziyu_rime PRIVATE rime-static android log)
target_compile_options(ziyu_rime PRIVATE -fvisibility=hidden)
