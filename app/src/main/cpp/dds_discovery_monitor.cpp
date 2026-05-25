#include <jni.h>

#include <android/log.h>

#include <fastdds/dds/domain/DomainParticipant.hpp>
#include <fastdds/dds/domain/DomainParticipantFactory.hpp>
#include <fastdds/dds/domain/DomainParticipantListener.hpp>
#include <fastdds/dds/domain/qos/DomainParticipantQos.hpp>
#include <fastdds/rtps/builtin/data/ParticipantProxyData.h>
#include <fastdds/rtps/builtin/data/ReaderProxyData.h>
#include <fastdds/rtps/builtin/data/WriterProxyData.h>
#include <fastdds/rtps/participant/ParticipantDiscoveryInfo.h>
#include <fastdds/rtps/reader/ReaderDiscoveryInfo.h>
#include <fastdds/rtps/writer/WriterDiscoveryInfo.h>

#include <chrono>
#include <map>
#include <mutex>
#include <sstream>
#include <string>

#define LOG_TAG "DdsDiscoveryMonitor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace fastdds_dds = eprosima::fastdds::dds;
namespace rtps = eprosima::fastrtps::rtps;

namespace {

long long now_millis()
{
    using namespace std::chrono;
    return duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count();
}

std::string guid_to_string(
        const rtps::GUID_t& guid)
{
    std::ostringstream output;
    output << guid;
    return output.str();
}

std::string json_escape(
        const std::string& value)
{
    std::string escaped;
    escaped.reserve(value.size() + 8);
    for (char ch : value)
    {
        switch (ch)
        {
            case '\\':
                escaped += "\\\\";
                break;
            case '"':
                escaped += "\\\"";
                break;
            case '\n':
                escaped += "\\n";
                break;
            case '\r':
                escaped += "\\r";
                break;
            case '\t':
                escaped += "\\t";
                break;
            default:
                escaped += ch;
                break;
        }
    }
    return escaped;
}

std::string participant_status_to_string(
        rtps::ParticipantDiscoveryInfo::DISCOVERY_STATUS status)
{
    switch (status)
    {
        case rtps::ParticipantDiscoveryInfo::DISCOVERED_PARTICIPANT:
        case rtps::ParticipantDiscoveryInfo::CHANGED_QOS_PARTICIPANT:
            return "VISIBLE";
        case rtps::ParticipantDiscoveryInfo::REMOVED_PARTICIPANT:
        case rtps::ParticipantDiscoveryInfo::DROPPED_PARTICIPANT:
            return "LOST";
        case rtps::ParticipantDiscoveryInfo::IGNORED_PARTICIPANT:
            return "IGNORED";
        default:
            return "UNKNOWN";
    }
}

std::string reader_status_to_string(
        rtps::ReaderDiscoveryInfo::DISCOVERY_STATUS status)
{
    switch (status)
    {
        case rtps::ReaderDiscoveryInfo::DISCOVERED_READER:
        case rtps::ReaderDiscoveryInfo::CHANGED_QOS_READER:
            return "VISIBLE";
        case rtps::ReaderDiscoveryInfo::REMOVED_READER:
            return "LOST";
        case rtps::ReaderDiscoveryInfo::IGNORED_READER:
            return "IGNORED";
        default:
            return "UNKNOWN";
    }
}

std::string writer_status_to_string(
        rtps::WriterDiscoveryInfo::DISCOVERY_STATUS status)
{
    switch (status)
    {
        case rtps::WriterDiscoveryInfo::DISCOVERED_WRITER:
        case rtps::WriterDiscoveryInfo::CHANGED_QOS_WRITER:
            return "VISIBLE";
        case rtps::WriterDiscoveryInfo::REMOVED_WRITER:
            return "LOST";
        case rtps::WriterDiscoveryInfo::IGNORED_WRITER:
            return "IGNORED";
        default:
            return "UNKNOWN";
    }
}

struct ParticipantRecord
{
    std::string guid;
    std::string name;
    std::string status;
    long long first_seen_at;
    long long last_seen_at;
};

struct EndpointRecord
{
    std::string guid;
    std::string participant_guid;
    std::string topic_name;
    std::string type_name;
    std::string kind;
    std::string status;
    long long first_seen_at;
    long long last_seen_at;
};

class DiscoveryMonitor :
    public fastdds_dds::DomainParticipantListener
{
public:
    bool start(
            int domain_id)
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (participant_ != nullptr)
        {
            return true;
        }

        domain_id_ = domain_id;
        started_at_ = now_millis();
        participants_.clear();
        endpoints_.clear();

        fastdds_dds::DomainParticipantQos qos = fastdds_dds::PARTICIPANT_QOS_DEFAULT;
        qos.name("RadioFieldRecorder");
        qos.wire_protocol().builtin.discovery_config.leaseDuration =
                eprosima::fastrtps::Duration_t(10, 0);
        qos.wire_protocol().builtin.discovery_config.leaseDuration_announcementperiod =
                eprosima::fastrtps::Duration_t(0, 500000000);
        qos.wire_protocol().builtin.discovery_config.initial_announcements.count = 10;
        qos.wire_protocol().builtin.discovery_config.initial_announcements.period =
                eprosima::fastrtps::Duration_t(0, 200000000);

        participant_ = fastdds_dds::DomainParticipantFactory::get_instance()->create_participant(
                static_cast<fastdds_dds::DomainId_t>(domain_id),
                qos,
                this);

        if (participant_ == nullptr)
        {
            LOGE("Failed to create DomainParticipant for domain_id=%d", domain_id);
            return false;
        }

        LOGI("DDS discovery monitor started for domain_id=%d", domain_id);
        return true;
    }

    void stop()
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (participant_ != nullptr)
        {
            fastdds_dds::DomainParticipantFactory::get_instance()->delete_participant(participant_);
            participant_ = nullptr;
        }
        LOGI("DDS discovery monitor stopped");
    }

    std::string snapshot_json()
    {
        std::lock_guard<std::mutex> lock(mutex_);
        std::ostringstream output;
        output << "{";
        output << "\"domainId\":" << domain_id_ << ",";
        output << "\"startedAt\":" << started_at_ << ",";
        output << "\"participants\":[";
        bool first = true;
        for (const auto& item : participants_)
        {
            const auto& participant = item.second;
            if (!first)
            {
                output << ",";
            }
            first = false;
            output << "{";
            output << "\"guid\":\"" << json_escape(participant.guid) << "\",";
            output << "\"name\":\"" << json_escape(participant.name) << "\",";
            output << "\"status\":\"" << participant.status << "\",";
            output << "\"firstSeenAt\":" << participant.first_seen_at << ",";
            output << "\"lastSeenAt\":" << participant.last_seen_at;
            output << "}";
        }
        output << "],\"endpoints\":[";
        first = true;
        for (const auto& item : endpoints_)
        {
            const auto& endpoint = item.second;
            if (!first)
            {
                output << ",";
            }
            first = false;
            output << "{";
            output << "\"guid\":\"" << json_escape(endpoint.guid) << "\",";
            output << "\"participantGuid\":\"" << json_escape(endpoint.participant_guid) << "\",";
            output << "\"topicName\":\"" << json_escape(endpoint.topic_name) << "\",";
            output << "\"typeName\":\"" << json_escape(endpoint.type_name) << "\",";
            output << "\"kind\":\"" << endpoint.kind << "\",";
            output << "\"status\":\"" << endpoint.status << "\",";
            output << "\"firstSeenAt\":" << endpoint.first_seen_at << ",";
            output << "\"lastSeenAt\":" << endpoint.last_seen_at;
            output << "}";
        }
        output << "]}";
        return output.str();
    }

    void on_participant_discovery(
            fastdds_dds::DomainParticipant* participant,
            rtps::ParticipantDiscoveryInfo&& info,
            bool& should_be_ignored) override
    {
        static_cast<void>(participant);
        should_be_ignored = false;

        std::lock_guard<std::mutex> lock(mutex_);
        const auto observed_at = now_millis();
        const auto guid = guid_to_string(info.info.m_guid);
        auto& record = participants_[guid];
        if (record.guid.empty())
        {
            record.guid = guid;
            record.first_seen_at = observed_at;
        }
        record.name = info.info.m_participantName.c_str();
        record.status = participant_status_to_string(info.status);
        record.last_seen_at = observed_at;
    }

    void on_subscriber_discovery(
            fastdds_dds::DomainParticipant* participant,
            rtps::ReaderDiscoveryInfo&& info) override
    {
        static_cast<void>(participant);
        std::lock_guard<std::mutex> lock(mutex_);
        const auto observed_at = now_millis();
        const auto guid = guid_to_string(info.info.guid());
        auto& record = endpoints_[guid];
        if (record.guid.empty())
        {
            record.guid = guid;
            record.first_seen_at = observed_at;
        }
        record.participant_guid = guid_prefix_to_string(info.info.guid());
        record.topic_name = info.info.topicName().c_str();
        record.type_name = info.info.typeName().c_str();
        record.kind = "READER";
        record.status = reader_status_to_string(info.status);
        record.last_seen_at = observed_at;
    }

    void on_publisher_discovery(
            fastdds_dds::DomainParticipant* participant,
            rtps::WriterDiscoveryInfo&& info) override
    {
        static_cast<void>(participant);
        std::lock_guard<std::mutex> lock(mutex_);
        const auto observed_at = now_millis();
        const auto guid = guid_to_string(info.info.guid());
        auto& record = endpoints_[guid];
        if (record.guid.empty())
        {
            record.guid = guid;
            record.first_seen_at = observed_at;
        }
        record.participant_guid = guid_prefix_to_string(info.info.guid());
        record.topic_name = info.info.topicName().c_str();
        record.type_name = info.info.typeName().c_str();
        record.kind = "WRITER";
        record.status = writer_status_to_string(info.status);
        record.last_seen_at = observed_at;
    }

private:
    std::string guid_prefix_to_string(
            const rtps::GUID_t& guid)
    {
        rtps::GUID_t participant_guid(guid.guidPrefix, 0x000001c1);
        return guid_to_string(participant_guid);
    }

    std::mutex mutex_;
    fastdds_dds::DomainParticipant* participant_ = nullptr;
    int domain_id_ = 0;
    long long started_at_ = 0;
    std::map<std::string, ParticipantRecord> participants_;
    std::map<std::string, EndpointRecord> endpoints_;
};

DiscoveryMonitor g_monitor;

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_miyabi0619_radiofieldrecorder_dds_DdsDiscoveryNativeBridge_nativeStart(
        JNIEnv* env,
        jobject thiz,
        jint domain_id)
{
    static_cast<void>(env);
    static_cast<void>(thiz);
    return g_monitor.start(domain_id) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_miyabi0619_radiofieldrecorder_dds_DdsDiscoveryNativeBridge_nativeStop(
        JNIEnv* env,
        jobject thiz)
{
    static_cast<void>(env);
    static_cast<void>(thiz);
    g_monitor.stop();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_miyabi0619_radiofieldrecorder_dds_DdsDiscoveryNativeBridge_nativeSnapshotJson(
        JNIEnv* env,
        jobject thiz)
{
    static_cast<void>(thiz);
    const auto snapshot = g_monitor.snapshot_json();
    return env->NewStringUTF(snapshot.c_str());
}
