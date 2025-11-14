cmake_minimum_required(VERSION 3.22)
project(EdgeDetector)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED OpenCV_DIR)
    set(OpenCV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../opencv-android-sdk")
endif()

find_package(OpenCV 4.5.0 REQUIRED)

include_directories(${OpenCV_INCLUDE_DIRS})

add_library(native-lib SHARED
    native-lib.cpp
    opencv-processor.cpp
)

target_link_libraries(native-lib
    ${OpenCV_LIBS}
    log
)

target_include_directories(native-lib PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)
