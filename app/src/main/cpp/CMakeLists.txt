cmake_minimum_required(VERSION 3.22.1)
project(client C)

find_package(shadowhook REQUIRED CONFIG)

add_library(client SHARED client.c)
target_link_libraries(client shadowhook::shadowhook log m)
target_link_options(client PRIVATE "-Wl,--gc-sections" "-Wl,-z,max-page-size=16384")
