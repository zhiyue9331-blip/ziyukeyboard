#include <jni.h>
#include <rime_api.h>

#include <mutex>
#include <string>
#include <vector>

namespace {
std::mutex engine_mutex;
RimeSessionId session_id = 0;
bool initialized = false;

std::string to_string(JNIEnv* env, jstring value) {
  if (!value) return {};
  const char* chars = env->GetStringUTFChars(value, nullptr);
  std::string result(chars ? chars : "");
  if (chars) env->ReleaseStringUTFChars(value, chars);
  return result;
}

jobjectArray to_java_array(JNIEnv* env, const std::vector<std::string>& values) {
  jclass string_class = env->FindClass("java/lang/String");
  jobjectArray output = env->NewObjectArray(values.size(), string_class, nullptr);
  for (size_t i = 0; i < values.size(); ++i) {
    env->SetObjectArrayElement(output, i, env->NewStringUTF(values[i].c_str()));
  }
  return output;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_hanziime_RimeNativeBridge_initialize(
    JNIEnv* env, jclass, jstring shared_dir, jstring user_dir) {
  std::lock_guard<std::mutex> lock(engine_mutex);
  if (initialized) return JNI_TRUE;
  std::string shared = to_string(env, shared_dir);
  std::string user = to_string(env, user_dir);
  RimeApi* api = rime_get_api();
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = shared.c_str();
  traits.user_data_dir = user.c_str();
  traits.app_name = "rime.ziyu.android";
  traits.distribution_name = "Ziyu IME";
  traits.distribution_code_name = "ziyu";
  traits.distribution_version = "0.4.0";
  traits.min_log_level = 2;
  traits.log_dir = "";
  api->setup(&traits);
  api->initialize(&traits);
  if (api->start_maintenance(True)) api->join_maintenance_thread();
  session_id = api->create_session();
  if (!session_id || !api->select_schema(session_id, "ziyu_pinyin")) {
    if (session_id) api->destroy_session(session_id);
    session_id = 0;
    api->finalize();
    return JNI_FALSE;
  }
  initialized = true;
  return JNI_TRUE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_hanziime_RimeNativeBridge_query(
    JNIEnv* env, jclass, jstring raw_input, jstring schema, jint limit) {
  std::lock_guard<std::mutex> lock(engine_mutex);
  std::vector<std::string> values;
  if (!initialized || !session_id) return to_java_array(env, values);
  RimeApi* api = rime_get_api();
  std::string requested_schema = to_string(env, schema);
  char current_schema[128] = {};
  if (!api->get_current_schema(session_id, current_schema, sizeof(current_schema))
      || requested_schema != current_schema) {
    if (!api->select_schema(session_id, requested_schema.c_str())) {
      return to_java_array(env, values);
    }
  }
  api->clear_composition(session_id);
  std::string input = to_string(env, raw_input);
  for (unsigned char key : input) api->process_key(session_id, key, 0);

  RimeCandidateListIterator iterator{};
  if (api->candidate_list_begin(session_id, &iterator)) {
    while (values.size() / 2 < static_cast<size_t>(limit)
           && api->candidate_list_next(&iterator)) {
      values.emplace_back(iterator.candidate.text ? iterator.candidate.text : "");
      values.emplace_back(iterator.candidate.comment ? iterator.candidate.comment : "");
    }
    api->candidate_list_end(&iterator);
  }
  return to_java_array(env, values);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_hanziime_RimeNativeBridge_selectCandidate(
    JNIEnv* env, jclass, jint index) {
  std::lock_guard<std::mutex> lock(engine_mutex);
  if (!initialized || !session_id) return env->NewStringUTF("");
  RimeApi* api = rime_get_api();
  if (!api->select_candidate(session_id, static_cast<size_t>(index))) {
    return env->NewStringUTF("");
  }
  RIME_STRUCT(RimeCommit, commit);
  if (!api->get_commit(session_id, &commit)) return env->NewStringUTF("");
  jstring result = env->NewStringUTF(commit.text ? commit.text : "");
  api->free_commit(&commit);
  return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_hanziime_RimeNativeBridge_finalizeEngine(JNIEnv*, jclass) {
  std::lock_guard<std::mutex> lock(engine_mutex);
  if (!initialized) return;
  RimeApi* api = rime_get_api();
  if (session_id) api->destroy_session(session_id);
  session_id = 0;
  api->finalize();
  initialized = false;
}
