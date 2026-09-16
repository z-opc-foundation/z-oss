import React from 'react'
import {Navigate, Route, Routes} from 'react-router-dom'
import Login from './pages/Login'
import AppLayout from './AppLayout'

function RequireAuth({children}) {
    const accessKey = localStorage.getItem('accessKey')
    if (!accessKey) {
        return <Navigate to="/login" replace/>
    }
    return children
}

function PublicRoute({children}) {
    const accessKey = localStorage.getItem('accessKey')
    if (accessKey) {
        return <Navigate to="/" replace/>
    }
    return children
}

export default function App() {
    return (
        <div id="app">
            <Routes>
                <Route
                    path="/login"
                    element={
                        <PublicRoute>
                            <Login/>
                        </PublicRoute>
                    }
                />
                <Route
                    path="/*"
                    element={
                        <RequireAuth>
                            <AppLayout/>
                        </RequireAuth>
                    }
                />
            </Routes>
        </div>
    )
}
