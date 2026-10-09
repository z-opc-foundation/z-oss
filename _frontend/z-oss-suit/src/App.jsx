import React from 'react'
import {Navigate, Route, Routes} from 'react-router-dom'
import {Login, isAuthenticated} from '@yuku123/z-oss-component/pages'
import AppLayout from './AppLayout'

function RequireAuth({children}) {
    if (!isAuthenticated()) return <Navigate to="/login" replace/>
    return children
}
function PublicRoute({children}) {
    if (isAuthenticated()) return <Navigate to="/" replace/>
    return children
}

export default function App() {
    return (
        <div id="app">
            <Routes>
                <Route path="/login" element={<PublicRoute><Login/></PublicRoute>}/>
                <Route path="/*" element={<RequireAuth><AppLayout/></RequireAuth>}/>
            </Routes>
        </div>
    )
}
