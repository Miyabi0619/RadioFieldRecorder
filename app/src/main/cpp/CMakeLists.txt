cmake_minimum_required(VERSION 3.22.1)

project("radio_field_recorder_native")

find_package(fastdds-prefab REQUIRED)

add_library(
    dds_discovery_monitor
    SHARED
    dds_discovery_monitor.cpp
)

target_include_directories(
    dds_discovery_monitor
    PRIVATE
    ${CMAKE_SOURCE_DIR}
    $<TARGET_PROPERTY:fastdds-prefab::fastrtps,INTERFACE_INCLUDE_DIRECTORIES>
    $<TARGET_PROPERTY:fastdds-prefab::fastcdr,INTERFACE_INCLUDE_DIRECTORIES>
)

target_link_libraries(
    dds_discovery_monitor
    fastdds-prefab::fastrtps
    fastdds-prefab::fastcdr
    log
)
