set(Boost_FOUND TRUE)
file(GLOB Boost_INCLUDE_DIRS LIST_DIRECTORIES TRUE "${BOOST_SOURCE}/libs/*/include")
set(Boost_LIBRARIES Boost::regex)
