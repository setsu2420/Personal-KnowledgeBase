pub(crate) mod sidecar;
mod tray;

use tauri::{Emitter, Manager};
use std::sync::Mutex;
use tauri_plugin_global_shortcut::ShortcutState;

pub(crate) struct BackendState(pub(crate) Mutex<Option<tokio::process::Child>>);

#[tauri::command]
fn backend_status(state: tauri::State<'_, BackendState>) -> String {
    let guard = state.0.lock().unwrap();
    if guard.is_some() {
        "running".to_string()
    } else {
        "stopped".to_string()
    }
}

#[tauri::command]
async fn start_backend(
    app: tauri::AppHandle,
    state: tauri::State<'_, BackendState>,
) -> Result<String, String> {
    let mut guard = state.0.lock().unwrap();
    if guard.is_some() {
        return Ok("already running".to_string());
    }

    let child = sidecar::start_spring_boot(&app).map_err(|e| e.to_string())?;
    *guard = Some(child);
    tray::update_tray_icon(&app, true);
    Ok("started".to_string())
}

#[tauri::command]
async fn stop_backend(
    app: tauri::AppHandle,
    state: tauri::State<'_, BackendState>,
) -> Result<String, String> {
    let child = {
        let mut guard = state.0.lock().unwrap();
        guard.take()
    }; // guard dropped before any .await

    if let Some(mut child) = child {
        sidecar::stop_spring_boot(&mut child).await.map_err(|e| e.to_string())?;
        tray::update_tray_icon(&app, false);
        Ok("stopped".to_string())
    } else {
        Ok("not running".to_string())
    }
}

#[tauri::command]
fn open_data_dir(app: tauri::AppHandle) -> Result<(), String> {
    tray::open_data_directory(&app);
    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.show();
                let _ = window.set_focus();
            }
        }))
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .manage(BackendState(Mutex::new(None)))
        .invoke_handler(tauri::generate_handler![
            backend_status,
            start_backend,
            stop_backend,
            open_data_dir,
        ])
        .setup(|app| {
            // 初始化系统托盘
            let app_handle = app.handle().clone();
            if let Err(e) = tray::setup_tray(&app_handle) {
                eprintln!("[tauri] Failed to setup tray: {}", e);
            }

            // 注册全局快捷键
            use tauri_plugin_global_shortcut::GlobalShortcutExt;

            // CmdOrCtrl+Q: 退出应用
            if let Err(e) = app_handle.global_shortcut().on_shortcut("CmdOrCtrl+Q", |app, _, event| {
                if event.state == ShortcutState::Pressed {
                    app.exit(0);
                }
            }) {
                eprintln!("[tauri] Failed to register shortcut CmdOrCtrl+Q: {}", e);
            }

            // CmdOrCtrl+W: 隐藏当前窗口到托盘
            if let Err(e) = app_handle.global_shortcut().on_shortcut("CmdOrCtrl+W", |app, _, event| {
                if event.state == ShortcutState::Pressed {
                    if let Some(window) = app.get_webview_window("main") {
                        let _ = window.hide();
                    }
                }
            }) {
                eprintln!("[tauri] Failed to register shortcut CmdOrCtrl+W: {}", e);
            }

            // CmdOrCtrl+,: 打开设置页面
            if let Err(e) = app_handle.global_shortcut().on_shortcut("CmdOrCtrl+,", |app, _, event| {
                if event.state == ShortcutState::Pressed {
                    let _ = app.emit("open-settings", ());
                }
            }) {
                eprintln!("[tauri] Failed to register shortcut CmdOrCtrl+,: {}", e);
            }

            // 应用启动时自动启动 Spring Boot 后端
            tauri::async_runtime::spawn(async move {
                tokio::time::sleep(tokio::time::Duration::from_millis(500)).await;
                match sidecar::start_spring_boot(&app_handle) {
                    Ok(child) => {
                        let state: tauri::State<'_, BackendState> = app_handle.state();
                        let mut guard = state.0.lock().unwrap();
                        *guard = Some(child);
                        println!("[tauri] Spring Boot backend started");
                        tray::update_tray_icon(&app_handle, true);
                    }
                    Err(e) => {
                        eprintln!("[tauri] Failed to start backend: {}", e);
                        tray::update_tray_icon(&app_handle, false);
                    }
                }
            });
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                // 关闭窗口时隐藏到托盘，而非退出
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
