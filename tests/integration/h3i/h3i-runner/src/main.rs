// Copyright (c) 2026 Oracle and/or its affiliates.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use std::env;
use std::time::Duration;

use h3i::actions::h3::send_headers_frame;
use h3i::actions::h3::send_headers_frame_literal;
use h3i::actions::h3::Action;
use h3i::actions::h3::StreamEvent;
use h3i::actions::h3::StreamEventType;
use h3i::actions::h3::WaitType;
use h3i::client::connection_summary::ConnectionSummary;
use h3i::client::sync_client;
use h3i::config::Config;
use h3i::frame::H3iFrame;
use h3i::quiche;
use h3i::HTTP3_CONTROL_STREAM_TYPE_ID;

const REQUEST_STREAM_ID: u64 = 0;
const CONTROL_STREAM_ID: u64 = 2;
const IDLE_TIMEOUT_MILLIS: u64 = 2000;
const WAIT_TIMEOUT: Duration = Duration::from_millis(500);

fn main() {
    if let Err(message) = run() {
        eprintln!("{message}");
        std::process::exit(1);
    }
}

fn run() -> Result<(), String> {
    let args: Vec<String> = env::args().collect();
    if args.len() != 4 {
        return Err(format!("Usage: {} <scenario> <host> <port>", args[0]));
    }

    let scenario = Scenario::parse(&args[1])?;
    let host = &args[2];
    let port: u16 = args[3]
        .parse()
        .map_err(|error| format!("Invalid port '{}': {error}", args[3]))?;

    let summary = sync_client::connect(config(host, port)?, scenario.actions(host), None)
        .map_err(|error| format!("h3i connection failed for '{}': {error:?}", scenario.name()))?;

    scenario.assert_summary(&summary)?;

    println!("Scenario '{}' passed.", scenario.name());
    println!(
        "{}",
        serde_json::to_string_pretty(&summary).unwrap_or_else(|error| error.to_string())
    );

    Ok(())
}

fn config(host: &str, port: u16) -> Result<Config, String> {
    Config::new()
        .with_host_port(format!("{host}:{port}"))
        .verify_peer(false)
        .with_idle_timeout(IDLE_TIMEOUT_MILLIS)
        .build()
        .map_err(|error| format!("Failed to create h3i config: {error}"))
}

#[derive(Copy, Clone)]
enum Scenario {
    ContentLengthMismatch,
    ReservedHttp2SettingOnControlStream,
    UppercaseHeaderName,
}

impl Scenario {
    fn parse(value: &str) -> Result<Self, String> {
        match value {
            "content-length-mismatch" => Ok(Self::ContentLengthMismatch),
            "reserved-http2-setting-on-control-stream" => {
                Ok(Self::ReservedHttp2SettingOnControlStream)
            }
            "uppercase-header-name" => Ok(Self::UppercaseHeaderName),
            _ => Err(format!("Unknown scenario '{value}'")),
        }
    }

    fn name(self) -> &'static str {
        match self {
            Self::ContentLengthMismatch => "content-length-mismatch",
            Self::ReservedHttp2SettingOnControlStream => "reserved-http2-setting-on-control-stream",
            Self::UppercaseHeaderName => "uppercase-header-name",
        }
    }

    fn actions(self, host: &str) -> Vec<Action> {
        match self {
            Self::ContentLengthMismatch => self.content_length_mismatch_actions(host),
            Self::ReservedHttp2SettingOnControlStream => self.reserved_http2_setting_actions(),
            Self::UppercaseHeaderName => self.uppercase_header_name_actions(host),
        }
    }

    fn assert_summary(self, summary: &ConnectionSummary) -> Result<(), String> {
        match self {
            Self::ContentLengthMismatch => assert_content_length_mismatch(summary),
            Self::ReservedHttp2SettingOnControlStream => assert_reserved_http2_setting(summary),
            Self::UppercaseHeaderName => assert_uppercase_header_name(summary),
        }
    }

    fn content_length_mismatch_actions(self, host: &str) -> Vec<Action> {
        let headers = vec![
            quiche::h3::Header::new(b":method", b"POST"),
            quiche::h3::Header::new(b":scheme", b"https"),
            quiche::h3::Header::new(b":authority", host.as_bytes()),
            quiche::h3::Header::new(b":path", b"/echo"),
            quiche::h3::Header::new(b"content-length", b"4"),
        ];

        vec![
            send_headers_frame(REQUEST_STREAM_ID, false, headers),
            Action::SendFrame {
                stream_id: REQUEST_STREAM_ID,
                fin_stream: true,
                frame: quiche::h3::frame::Frame::Data {
                    payload: b"pay".to_vec(),
                },
            },
            Action::Wait {
                wait_type: WaitType::StreamEvent(StreamEvent {
                    stream_id: REQUEST_STREAM_ID,
                    event_type: StreamEventType::Finished,
                }),
            },
            Action::ConnectionClose {
                error: no_error_close(),
            },
        ]
    }

    fn reserved_http2_setting_actions(self) -> Vec<Action> {
        vec![
            Action::OpenUniStream {
                stream_id: CONTROL_STREAM_ID,
                fin_stream: false,
                stream_type: HTTP3_CONTROL_STREAM_TYPE_ID,
            },
            Action::SendFrame {
                stream_id: CONTROL_STREAM_ID,
                fin_stream: false,
                frame: quiche::h3::frame::Frame::Settings {
                    max_field_section_size: None,
                    qpack_max_table_capacity: None,
                    qpack_blocked_streams: None,
                    connect_protocol_enabled: None,
                    h3_datagram: None,
                    grease: None,
                    additional_settings: Some(vec![(0x02, 1)]),
                    raw: None,
                },
            },
            Action::Wait {
                wait_type: WaitType::WaitDuration(WAIT_TIMEOUT),
            },
        ]
    }

    fn uppercase_header_name_actions(self, host: &str) -> Vec<Action> {
        let headers = vec![
            quiche::h3::Header::new(b":method", b"GET"),
            quiche::h3::Header::new(b":scheme", b"https"),
            quiche::h3::Header::new(b":authority", host.as_bytes()),
            quiche::h3::Header::new(b":path", b"/index.html"),
            quiche::h3::Header::new(b"X-Test", b"value"),
        ];

        vec![
            send_headers_frame_literal(REQUEST_STREAM_ID, true, headers),
            Action::Wait {
                wait_type: WaitType::StreamEvent(StreamEvent {
                    stream_id: REQUEST_STREAM_ID,
                    event_type: StreamEventType::Finished,
                }),
            },
            Action::ConnectionClose {
                error: no_error_close(),
            },
        ]
    }
}

fn no_error_close() -> quiche::ConnectionError {
    quiche::ConnectionError {
        is_app: true,
        error_code: quiche::h3::WireErrorCode::NoError as u64,
        reason: Vec::new(),
    }
}

fn assert_content_length_mismatch(summary: &ConnectionSummary) -> Result<(), String> {
    let reset = summary
        .stream_map
        .stream(REQUEST_STREAM_ID)
        .into_iter()
        .find_map(|frame| match frame {
            H3iFrame::ResetStream(reset) => Some(reset),
            _ => None,
        })
        .ok_or_else(|| {
            format!(
                "Expected request stream reset for content-length mismatch.\n\n{}",
                summary_json(summary)
            )
        })?;

    let expected = quiche::h3::WireErrorCode::MessageError as u64;
    if reset.error_code != expected {
        return Err(format!(
            "Expected H3_MESSAGE_ERROR ({expected}) for content-length mismatch, got {}.\n\n{}",
            reset.error_code,
            summary_json(summary)
        ));
    }

    Ok(())
}

fn assert_reserved_http2_setting(summary: &ConnectionSummary) -> Result<(), String> {
    let peer_error = summary.conn_close_details.peer_error().ok_or_else(|| {
        format!(
            "Expected peer connection close for reserved HTTP/2 setting.\n\n{}",
            summary_json(summary)
        )
    })?;

    if !peer_error.is_app {
        return Err(format!(
                "Expected application-level close for reserved HTTP/2 setting, got transport close.\n\n{}",
                summary_json(summary)
        ));
    }

    let expected = quiche::h3::WireErrorCode::SettingsError as u64;
    if peer_error.error_code != expected {
        return Err(format!(
            "Expected H3_SETTINGS_ERROR ({expected}), got {}.\n\n{}",
            peer_error.error_code,
            summary_json(summary)
        ));
    }

    Ok(())
}

fn assert_uppercase_header_name(summary: &ConnectionSummary) -> Result<(), String> {
    let reset = summary
        .stream_map
        .stream(REQUEST_STREAM_ID)
        .into_iter()
        .find_map(|frame| match frame {
            H3iFrame::ResetStream(reset) => Some(reset),
            _ => None,
        })
        .ok_or_else(|| {
            format!(
                "Expected request stream reset for uppercase header name.\n\n{}",
                summary_json(summary)
            )
        })?;

    let expected = quiche::h3::WireErrorCode::MessageError as u64;
    if reset.error_code != expected {
        return Err(format!(
            "Expected H3_MESSAGE_ERROR ({expected}) for uppercase header name, got {}.\n\n{}",
            reset.error_code,
            summary_json(summary)
        ));
    }

    Ok(())
}

fn summary_json(summary: &ConnectionSummary) -> String {
    serde_json::to_string_pretty(summary).unwrap_or_else(|error| error.to_string())
}
